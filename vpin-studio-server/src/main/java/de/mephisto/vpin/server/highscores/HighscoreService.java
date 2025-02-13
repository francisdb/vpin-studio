package de.mephisto.vpin.server.highscores;

import com.google.common.annotations.VisibleForTesting;
import de.mephisto.vpin.restclient.HighscoreType;
import de.mephisto.vpin.restclient.PreferenceNames;
import de.mephisto.vpin.server.competitions.CompetitionsRepository;
import de.mephisto.vpin.server.competitions.RankedPlayer;
import de.mephisto.vpin.server.competitions.ScoreSummary;
import de.mephisto.vpin.server.games.Game;
import de.mephisto.vpin.server.players.Player;
import de.mephisto.vpin.server.preferences.PreferencesService;
import de.mephisto.vpin.server.system.SystemService;
import de.mephisto.vpin.server.util.vpreg.VPReg;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HighscoreService implements InitializingBean {
  private final static Logger LOG = LoggerFactory.getLogger(HighscoreService.class);

  @Autowired
  private SystemService systemService;

  @Autowired
  private HighscoreRepository highscoreRepository;

  @Autowired
  private HighscoreVersionRepository highscoreVersionRepository;

  @Autowired
  private CompetitionsRepository competitionsRepository;

  @Autowired
  private HighscoreParser highscoreParser;

  @Autowired
  private PreferencesService preferencesService;

  private boolean pauseChangeEvents;

  private HighscoreResolver highscoreResolver;

  private final List<HighscoreChangeListener> listeners = new ArrayList<>();

  public boolean resetHighscore(@NonNull Game game) {
    try {
      HighscoreType highscoreType = game.getHighscoreType();
      boolean result = false;
      if (highscoreType != null) {
        switch (highscoreType) {
          case EM: {
            result = game.getEMHighscoreFile() != null && game.getEMHighscoreFile().exists() && game.getEMHighscoreFile().delete();
            break;
          }
          case NVRam: {
            result = game.getNvRamFile().exists() && game.getNvRamFile().delete();
            break;
          }
          case VPReg: {
            VPReg reg = new VPReg(systemService.getVPRegFile(), game.getRom(), game.getTableName());
            result = reg.resetHighscores();
            break;
          }
          default: {
            LOG.error("No matching highscore type found for '" + highscoreType + "'");
          }
        }
      }
      else {
        result = true;
      }
      deleteScores(game.getId());
      return result;
    } catch (Exception e) {
      LOG.error("Failed to reset highscore: " + e.getMessage(), e);
    }
    return false;
  }

  @NonNull
  public List<Score> parseScores(Date createdAt, String raw, int gameId, long serverId) {
    return highscoreParser.parseScores(createdAt, raw, gameId, serverId);
  }

  @NonNull
  public List<RankedPlayer> getPlayersByRanks() {
    Map<String, RankedPlayer> playerMap = new HashMap<>();
    List<ScoreSummary> highscoresWithScore = getHighscoresWithScore();
    for (ScoreSummary summary : highscoresWithScore) {
      if (summary.getScores().size() >= 3) {
        List<Score> scores = summary.getScores();
        for (int i = 0; i < scores.size(); i++) {
          Score score = scores.get(i);
          if (score.getPlayer() == null) {
            continue;
          }

          if (!playerMap.containsKey(score.getPlayerInitials())) {
            RankedPlayer p = new RankedPlayer();
            Player player = score.getPlayer();
            p.setAvatarUrl(player.getAvatarUrl());
            if (player.getAvatar() != null) {
              p.setAvatarUuid(player.getAvatar().getUuid());
            }
            p.setName(player.getName());
            p.setInitials(player.getInitials());
            p.setCompetitionsWon(competitionsRepository.findByWinnerInitials(player.getInitials()).size());
            playerMap.put(score.getPlayerInitials(), p);
          }

          RankedPlayer player = playerMap.get(score.getPlayerInitials());
          player.addBy(i);
        }
      }
    }

    String rankingPoints = (String) preferencesService.getPreferenceValue(PreferenceNames.RANKING_POINTS, "4,2,1,0");
    List<Integer> points = Arrays.stream(rankingPoints.split(",")).map(Integer::parseInt).collect(Collectors.toList());

    List<RankedPlayer> rankedPlayers = new ArrayList<>(playerMap.values());
    for (RankedPlayer rankedPlayer : rankedPlayers) {
      rankedPlayer.setPoints(rankedPlayer.getPoints() + (points.get(0) * rankedPlayer.getFirst()));
      rankedPlayer.setPoints(rankedPlayer.getPoints() + (points.get(1) * rankedPlayer.getSecond()));
      rankedPlayer.setPoints(rankedPlayer.getPoints() + (points.get(2) * rankedPlayer.getThird()));
      rankedPlayer.setPoints(rankedPlayer.getPoints() + (points.get(3) * rankedPlayer.getCompetitionsWon()));
    }

    rankedPlayers.sort((o2, o1) -> o1.getPoints() - o2.getPoints());
    for (int i = 1; i <= rankedPlayers.size(); i++) {
      rankedPlayers.get(i - 1).setRank(i);
    }
    return rankedPlayers;
  }

  public List<ScoreSummary> getHighscoresWithScore() {
    List<ScoreSummary> result = new ArrayList<>();
    List<Highscore> byRawIsNotNull = highscoreRepository.findByRawIsNotNull();
    long serverId = preferencesService.getPreferenceValueLong(PreferenceNames.DISCORD_GUILD_ID, -1);
    for (Highscore highscore : byRawIsNotNull) {
      List<Score> scores = highscoreParser.parseScores(highscore.getLastModified(), highscore.getRaw(), highscore.getGameId(), serverId);
      result.add(new ScoreSummary(scores, highscore.getCreatedAt()));
    }
    return result;
  }

  public ScoreList getScoreHistory(int gameId) {
    long serverId = preferencesService.getPreferenceValueLong(PreferenceNames.DISCORD_GUILD_ID, -1);
    return getScoresBetween(gameId, new Date(0), new Date(), serverId);
  }

  /**
   * Returns all available scores for the game with the given id and time frame
   */
  public ScoreList getScoresBetween(int gameId, Date start, Date end, long serverId) {
    ScoreList scoreList = new ScoreList();
    List<HighscoreVersion> byGameIdAndCreatedAtBetween = highscoreVersionRepository.findByGameIdAndCreatedAtBetween(gameId, start, end);
    for (HighscoreVersion version : byGameIdAndCreatedAtBetween) {
      ScoreSummary scoreSummary = getScoreSummary(version.getCreatedAt(), version.getNewRaw(), gameId, serverId);
      scoreList.getScores().add(scoreSummary);
    }
    scoreList.getScores().sort(Comparator.comparing(ScoreSummary::getCreatedAt));

    if (!scoreList.getScores().isEmpty()) {
      scoreList.setLatestScore(scoreList.getScores().get(0));
    }
    return scoreList;
  }

  public ScoreSummary getAllHighscoresForPlayer(String initials) {
    long serverId = preferencesService.getPreferenceValueLong(PreferenceNames.DISCORD_GUILD_ID, -1);
    return getAllHighscoresForPlayer(serverId, initials);
  }

  /**
   * Returns a list of all scores that are available for the player with the given initials
   *
   * @param initials the initials to filter for
   * @return all highscores of the given player
   */
  public ScoreSummary getAllHighscoresForPlayer(long serverId, String initials) {
    ScoreSummary summary = new ScoreSummary(new ArrayList<>(), new Date());
    List<Highscore> all = highscoreRepository.findAllByOrderByCreatedAtDesc();
    for (Highscore highscore : all) {
      if (StringUtils.isEmpty(highscore.getRaw())) {
        continue;
      }

      List<Score> scores = parseScores(highscore.getCreatedAt(), highscore.getRaw(), highscore.getGameId(), serverId);
      for (Score score : scores) {
        if (score.getPlayerInitials().equalsIgnoreCase(initials)) {
          summary.getScores().add(score);
        }
      }
    }
    return summary;
  }

  /**
   * Returns a list of all scores for the given game
   *
   * @param gameId      the game to retrieve the highscores for
   * @param displayName the optional display name/name of the table the summary is for
   * @return all highscores of the given player
   */
  @NonNull
  public ScoreSummary getScoreSummary(long serverId, int gameId, @Nullable String displayName) {
    ScoreSummary summary = new ScoreSummary(new ArrayList<>(), new Date());
    Optional<Highscore> highscore = highscoreRepository.findByGameId(gameId);
    if (highscore.isPresent()) {
      Highscore h = highscore.get();
      if (!StringUtils.isEmpty(h.getRaw())) {
        List<Score> scores = parseScores(h.getCreatedAt(), h.getRaw(), gameId, serverId);
        summary.setRaw(h.getRaw());
        summary.getScores().addAll(scores);
      }

      HighscoreMetadata metadata = new HighscoreMetadata();
      metadata.setDisplayName(displayName);
      metadata.setModified(h.getLastModified());
      metadata.setScanned(h.getLastScanned());
      metadata.setFilename(h.getFilename());
      metadata.setType(h.getType() != null ? HighscoreType.valueOf(h.getType()) : null);
      metadata.setStatus(h.getStatus());
      summary.setMetadata(metadata);
    }
    else {
      HighscoreMetadata metadata = new HighscoreMetadata();
      metadata.setDisplayName(displayName);
      metadata.setStatus(HighscoreResolver.NO_SCORE_FOUND_MSG);
      summary.setMetadata(metadata);
    }
    return summary;
  }

  /**
   * Used for the dashboard widget to show the list of newly created highscores
   */
  public List<Score> getAllHighscoreVersions() {
    List<Score> scores = new ArrayList<>();
    List<HighscoreVersion> all = highscoreVersionRepository.findAllByOrderByCreatedAtDesc();
    long serverId = preferencesService.getPreferenceValueLong(PreferenceNames.DISCORD_GUILD_ID, -1);

    for (HighscoreVersion version : all) {
      //ignore initial versions
      if (version.getChangedPosition() < 0) {
        continue;
      }

      List<Score> versionScores = highscoreParser.parseScores(version.getCreatedAt(), version.getNewRaw(), version.getGameId(), serverId);
      //change positions start with 1!
      if (version.getChangedPosition() > versionScores.size()) {
        LOG.error("Found invalid change position '" + version.getChangedPosition() + "' for " + version);
      }
      else {
        int changedPos = version.getChangedPosition() - 1;
        scores.add(versionScores.get(changedPos));
      }

    }
    return scores;
  }

  public void addHighscoreChangeListener(@NonNull HighscoreChangeListener listener) {
    this.listeners.add(listener);
  }

  /**
   * Collects a list of highscores for serialization
   *
   * @param createdAt the date the highscores have been created
   * @param raw       the raw data
   * @param gameId    the gameId of the game
   * @param serverId  the discord server id
   */
  private ScoreSummary getScoreSummary(Date createdAt, String raw, int gameId, long serverId) {
    List<Score> scores = parseScores(createdAt, raw, gameId, serverId);
    if (scores.size() > 0) {
      return new ScoreSummary(scores, createdAt);
    }
    return null;
  }


  @NonNull
  public Optional<Highscore> getOrCreateHighscore(@NonNull Game game) {
    Optional<Highscore> highscore = highscoreRepository.findByGameId(game.getId());
    if (highscore.isEmpty() && !StringUtils.isEmpty(game.getRom())) {
      HighscoreMetadata metadata = scanScore(game);
      return updateHighscore(game, metadata);
    }
    return highscore;
  }

  @NonNull
  public HighscoreMetadata scanScore(@NonNull Game game) {
    HighscoreMetadata highscoreMetadata = highscoreResolver.readHighscore(game);
    updateHighscore(game, highscoreMetadata);
    return highscoreMetadata;
  }

  /**
   * Rules for highscore versioning:
   * - a Highscore record is only created, when an RAW data is present
   * - for the first record Highscore, a version is created too with an empty OLD value
   * - for every newly recorded Highscore, an additional version is stored
   *
   * @param game     the game to update the highscore for
   * @param metadata the extracted metadata from the system
   * @return the highscore optional if a score could be extracted
   */
  @VisibleForTesting
  protected Optional<Highscore> updateHighscore(@NonNull Game game, @NonNull HighscoreMetadata metadata) {
    //we don't do anything if not value is extract, this may lead to superflous system calls, but we have time
    if (StringUtils.isEmpty(metadata.getRaw())) {
      return Optional.empty();
    }

    boolean initialScore = false;

    //first highscore parsing situation: store the first record and the first version
    Optional<Highscore> existingHighscore = highscoreRepository.findByGameId(game.getId());
    if (existingHighscore.isEmpty()) {
      initialScore = true;
      Highscore newHighscore = Highscore.forGame(game, metadata);

      //create artificial empty init version
      List<Score> newScores = highscoreParser.parseScores(newHighscore.getLastModified(), newHighscore.getRaw(), game.getId(), -1);
      StringBuilder emptyRaw = new StringBuilder("HIGHEST SCORES\n");
      for (Score newScore : newScores) {
        emptyRaw.append("#");
        emptyRaw.append(newScore.getPosition());
        emptyRaw.append(" ");
        emptyRaw.append("???");
        emptyRaw.append("   ");
        emptyRaw.append("0");
        emptyRaw.append("\n");
      }
      newHighscore.setRaw(emptyRaw.toString());
      Highscore updatedNewHighScore = highscoreRepository.save(newHighscore);

      HighscoreVersion highscoreVersion = newHighscore.toVersion(-1, emptyRaw.toString());
      //!!! this is the first highscore version, so the old RAW value must be corrected to NULL
      highscoreVersion.setOldRaw(null);
      highscoreVersionRepository.saveAndFlush(highscoreVersion);

//      //we are finished here since we cannot calculate any difference for the first score
//      return Optional.of(updatedNewHighScore);
      existingHighscore = Optional.of(updatedNewHighScore);
    }

    Highscore newHighscore = Highscore.forGame(game, metadata);
    Highscore oldHighscore = existingHighscore.get();

    //check if there is a difference
    String oldRaw = oldHighscore.getRaw();
    String newRaw = newHighscore.getRaw();

    if (oldRaw == null || newRaw == null) {
      LOG.error("The highscore data of \"" + game.getGameDisplayName() + "\" has become invalid, no RAW data can be extracted anymore.");
      return Optional.of(oldHighscore);
    }

    if (oldRaw.equals(newRaw)) {
      LOG.info("Skipped highscore change event for {} because the no score change for rom '{}' detected.", game, game.getRom());
      return Optional.of(oldHighscore);
    }

    /*
     * Diff calculation:
     * Note that this only determines if the highscore has changed locally and a change event should be fired.
     */
    long serverId = preferencesService.getPreferenceValueLong(PreferenceNames.DISCORD_GUILD_ID, -1);
    List<Score> newScores = highscoreParser.parseScores(newHighscore.getLastModified(), newHighscore.getRaw(), game.getId(), serverId);
    List<Score> oldScores = getOrCloneOldHighscores(oldHighscore, game, oldRaw, serverId, newScores);

    if (!oldScores.isEmpty()) {
      List<Integer> changedPositions = calculateChangedPositions(oldScores, newScores);
      if (changedPositions.isEmpty()) {
        LOG.info("No highscore change of rom '" + game.getRom() + "' detected for " + game + ", skipping notification event.");
        return Optional.of(oldHighscore);
      }

      LOG.info("Calculated changed positions for '" + game.getRom() + "': " + changedPositions);
      for (Integer changedPosition : changedPositions) {
        //so we have a highscore update, let's decide the distribution
        Score oldScore = oldScores.get(changedPosition - 1);
        Score newScore = newScores.get(changedPosition - 1);

        //archive old existingScore only if it had actual data
        if (!StringUtils.isEmpty(oldRaw)) {
          HighscoreVersion version = oldHighscore.toVersion(changedPosition, newRaw);
          highscoreVersionRepository.saveAndFlush(version);
          LOG.info("Created highscore version for " + game + ", changed position " + changedPosition);
        }

        //update existing one
        oldHighscore.setRaw(newHighscore.getRaw());
        oldHighscore.setType(newHighscore.getType());
        oldHighscore.setLastScanned(newHighscore.getLastScanned());
        oldHighscore.setLastModified(newHighscore.getLastModified());
        oldHighscore.setFilename(newHighscore.getFilename());
        oldHighscore.setStatus(null);
        oldHighscore.setDisplayName(newHighscore.getDisplayName());
        highscoreRepository.saveAndFlush(oldHighscore);
        LOG.info("Saved updated highscore for " + game);

        //finally, fire the update event to notify all listeners
        HighscoreChangeEvent event = new HighscoreChangeEvent(game, oldScore, newScore, oldScores.size(), initialScore);
        triggerHighscoreChange(event);
      }
    }

    return Optional.of(oldHighscore);
  }

  /**
   * The old highscore may be empty if a competitions did reset them.
   */
  @NotNull
  private List<Score> getOrCloneOldHighscores(Highscore oldHighscore, Game game, String oldRaw, long serverId, List<Score> newScores) {
    List<Score> oldScores = new ArrayList<>();
    if (oldRaw != null) {
      oldScores = highscoreParser.parseScores(oldHighscore.getLastModified(), oldHighscore.getRaw(), game.getId(), serverId);
    }
    else {
      for (Score newScore : newScores) {
        Score score = new Score(newScore.getCreatedAt(), newScore.getGameId(), "???", null, "0", 0, newScore.getPosition());
        oldScores.add(score);
      }
    }
    return oldScores;
  }

  public void deleteScores(int gameId) {
    Optional<Highscore> byGameId = highscoreRepository.findByGameId(gameId);
    byGameId.ifPresent(highscore -> highscoreRepository.delete(highscore));
    LOG.info("Deleted latest highscore for " + gameId);

    List<HighscoreVersion> versions = highscoreVersionRepository.findByGameId(gameId);
    highscoreVersionRepository.deleteAll(versions);
    LOG.info("Deleted all highscore versions for " + gameId);
  }

  /**
   * Returns the highscore difference position, starting from 1.
   */
  public List<Integer> calculateChangedPositions(@NonNull List<Score> oldScores, @NonNull List<Score> newScores) {
    List<Integer> changes = new ArrayList<>();
    try {
      for (int i = 0; i < newScores.size(); i++) {
        Score newScore = newScores.get(i);
        if (newScore.getPlayerInitials().equals("???") || newScore.getNumericScore() == 0) {
          continue;
        }

        boolean notFound = oldScores.stream().noneMatch(score -> score.matches(newScore));
        if (notFound) {
          changes.add(newScore.getPosition());
          LOG.info("Calculated changed score: [" + newScore + "] has beaten [" + oldScores.get(newScore.getPosition() - 1) + "]");
        }
      }
    } catch (Exception e) {
      LOG.info("Failed to calculate score change: " + e.getMessage(), e);
    }
    return changes;
  }

  public int calculateChangedPositionByScore(@NonNull List<Score> oldScores, @NonNull Score newScore) {
    for (int i = 0; i < oldScores.size(); i++) {
      if (oldScores.get(i).getNumericScore() < newScore.getNumericScore()) {
        LOG.info("Calculated changed score at position " + (i + 1) + ": [" + newScore + "] has beaten [" + oldScores.get(i) + "]");
        return i + 1;
      }
    }
    return -1;
  }

  public void setPauseChangeEvents(boolean pauseChangeEvents) {
    this.pauseChangeEvents = pauseChangeEvents;
    LOG.info("Setting highscore change events to: " + pauseChangeEvents);
  }

  private void triggerHighscoreChange(@NonNull HighscoreChangeEvent event) {
    if (pauseChangeEvents) {
      LOG.info("Skipping highscore change event because change events are paused.");
      return;
    }

    for (HighscoreChangeListener listener : listeners) {
      listener.highscoreChanged(event);
    }
  }

  @Override
  public void afterPropertiesSet() {
    this.highscoreResolver = new HighscoreResolver(systemService);
  }
}
