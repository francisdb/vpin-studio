package de.mephisto.vpin.server.games;

import de.mephisto.vpin.commons.utils.FileUtils;
import de.mephisto.vpin.restclient.HighscoreType;
import de.mephisto.vpin.restclient.PreferenceNames;
import de.mephisto.vpin.restclient.descriptors.DeleteDescriptor;
import de.mephisto.vpin.restclient.popper.Emulator;
import de.mephisto.vpin.restclient.popper.PopperScreen;
import de.mephisto.vpin.restclient.popper.TableDetails;
import de.mephisto.vpin.restclient.representations.ValidationState;
import de.mephisto.vpin.server.altcolor.AltColorService;
import de.mephisto.vpin.server.altsound.AltSoundService;
import de.mephisto.vpin.server.assets.Asset;
import de.mephisto.vpin.server.assets.AssetRepository;
import de.mephisto.vpin.server.competitions.ScoreSummary;
import de.mephisto.vpin.server.highscores.*;
import de.mephisto.vpin.server.highscores.cards.CardService;
import de.mephisto.vpin.server.popper.GameMediaItem;
import de.mephisto.vpin.server.popper.PinUPConnector;
import de.mephisto.vpin.server.popper.PopperService;
import de.mephisto.vpin.server.preferences.PreferencesService;
import de.mephisto.vpin.server.puppack.PupPack;
import de.mephisto.vpin.server.puppack.PupPacksService;
import de.mephisto.vpin.server.roms.RomService;
import de.mephisto.vpin.server.roms.ScanResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GameService {
  private final static Logger LOG = LoggerFactory.getLogger(GameService.class);

  @Autowired
  private PinUPConnector pinUPConnector;

  @Autowired
  private RomService romService;

  @Autowired
  private GameDetailsRepository gameDetailsRepository;

  @Autowired
  private ValidationService gameValidator;

  @Autowired
  private HighscoreService highscoreService;

  @Autowired
  private PreferencesService preferencesService;

  @Autowired
  private CardService cardService;

  @Autowired
  private AssetRepository assetRepository;

  @Autowired
  private PupPacksService pupPackService;

  @Autowired
  private AltSoundService altSoundService;

  @Autowired
  private AltColorService altColorService;

  @SuppressWarnings("unused")
  public List<Game> getGames() {
    long start = System.currentTimeMillis();
    List<Game> games = new ArrayList<>(pinUPConnector.getGames());
    LOG.info("Game fetch took " + (System.currentTimeMillis() - start) + "ms., returned " + games.size() + " tables.");
    start = System.currentTimeMillis();

    for (Game game : games) {
      applyGameDetails(game, false);
    }
    LOG.info("Game details fetch took " + (System.currentTimeMillis() - start) + "ms.");
    return games;
  }

  public List<Game> getGamesByRom(@NonNull String rom) {
    List<Game> games = this.getGames()
        .stream()
        .filter(g ->
            (!StringUtils.isEmpty(g.getRom()) && g.getRom().equals(rom)) ||
                (!StringUtils.isEmpty(g.getTableName()) && g.getTableName().equals(rom)))
        .collect(Collectors.toList());
    for (Game game : games) {
      applyGameDetails(game, false);
    }
    return games;
  }

  public boolean resetGame(int gameId) {
    Game game = this.getGame(gameId);
    if (game == null) {
      return false;
    }
    return highscoreService.resetHighscore(game);
  }

  public boolean deleteGame(@NonNull DeleteDescriptor descriptor) {
    List<Integer> gameIds = descriptor.getGameIds();
    boolean success = true;

    for (Integer gameId : gameIds) {
      Game game = this.getGame(gameId);
      if (game == null) {
        return false;
      }

      if (descriptor.isDeleteHighscores()) {
        resetGame(game.getId());
      }

      if (descriptor.isDeleteTable()) {
        if (!FileUtils.delete(game.getGameFile())) {
          success = false;
        }

        if (!FileUtils.delete(game.getPOVFile())) {
          success = false;
        }

        if (!FileUtils.delete(game.getResFile())) {
          success = false;
        }
      }

      if (descriptor.isDeleteDirectB2s()) {
        if (!FileUtils.delete(game.getCroppedDefaultPicture())) {
          success = false;
        }
        if (!FileUtils.delete(game.getRawDefaultPicture())) {
          success = false;
        }
        if (!FileUtils.delete(game.getDirectB2SFile())) {
          success = false;
        }
      }

      if (descriptor.isDeletePupPack()) {
        PupPack pupPack = game.getPupPack();
        if (pupPack != null && !pupPack.delete()) {
          success = false;
        }
      }

      if (descriptor.isDeleteDMDs()) {
        if (!FileUtils.deleteFolder(game.getFlexDMDFolder())) {
          success = false;
        }

        if (!FileUtils.deleteFolder(game.getUltraDMDFolder())) {
          success = false;
        }
      }

      if (descriptor.isDeleteAltSound()) {
        if (altSoundService.delete(game)) {
          success = false;
        }
      }


      if (descriptor.isDeleteAltColor()) {
        if (game.getAltColorFolder() != null && !FileUtils.deleteFolder(game.getAltColorFolder())) {
          success = false;
        }
      }

      if (descriptor.isDeleteCfg()) {
        if (game.getCfgFile() != null && !FileUtils.delete(game.getCfgFile())) {
          success = false;
        }
      }


      if (descriptor.isDeleteMusic()) {
        if (game.getMusicFolder() != null && !FileUtils.deleteFolder(game.getMusicFolder())) {
          success = false;
        }
      }

      GameDetails byPupId = gameDetailsRepository.findByPupId(game.getId());
      if (byPupId != null) {
        gameDetailsRepository.delete(byPupId);
      }

      if (descriptor.isDeleteFromPopper()) {
        if (!pinUPConnector.deleteGame(gameId)) {
          success = false;
        }

        highscoreService.deleteScores(game.getId());

        PopperScreen[] values = PopperScreen.values();
        for (PopperScreen originalScreenValue : values) {
          List<GameMediaItem> gameMediaItem = game.getGameMedia().getMediaItems(originalScreenValue);
          for (GameMediaItem mediaItem : gameMediaItem) {
            File mediaFile = mediaItem.getFile();
            if (!mediaFile.delete() && success) {
              success = false;
            }
          }
        }
      }

      Optional<Asset> byId = assetRepository.findByExternalId(String.valueOf(gameId));
      byId.ifPresent(asset -> assetRepository.delete(asset));

      LOG.info("Deleted " + game.getGameDisplayName());
    }
    return success;
  }

  public List<Integer> getGameId() {
    return this.pinUPConnector.getGameIds();
  }

  @SuppressWarnings("unused")
  @Nullable
  public Game getGame(int id) {
    Game game = pinUPConnector.getGame(id);
    if (game != null) {
      applyGameDetails(game, false);
      return game;
    }
    return null;
  }

  /**
   * Retursn the current highscore for the given game
   *
   * @param gameId
   * @return
   */
  public ScoreSummary getScores(int gameId) {
    long serverId = preferencesService.getPreferenceValueLong(PreferenceNames.DISCORD_GUILD_ID, -1);
    return highscoreService.getScoreSummary(serverId, gameId, null);
  }

  /**
   * Returns a complete list of highscore versions
   *
   * @param gameId
   * @return
   */
  public ScoreList getScoreHistory(int gameId) {
    return highscoreService.getScoreHistory(gameId);
  }

  public ScoreSummary getRecentHighscores(int count) {
    List<Score> scores = new ArrayList<>();
    ScoreSummary summary = new ScoreSummary(scores, null);
    List<Score> allHighscoreVersions = highscoreService.getAllHighscoreVersions();

    //check if the actual game still exists
    for (Score version : allHighscoreVersions) {
      Game rawGame = pinUPConnector.getGame(version.getGameId());
      if (rawGame != null && !scores.contains(version)) {
        scores.add(version);
      }

      if (count > 0 && scores.size() == count) {
        return summary;
      }
    }

    return summary;
  }

  /**
   * Returns true if game details are available
   *
   * @param gameId the game to scan
   */
  @Nullable
  public Game scanGame(int gameId) {
    Game game = null;
    try {
      game = getGame(gameId);
      if (game != null) {
        Emulator emulator = game.getEmulator();
        if (!Emulator.isVisualPinball(emulator.getName())) {
          return game;
        }

        applyGameDetails(game, true);
        highscoreService.scanScore(game);

        return getGame(gameId);
      }
      else {
        LOG.error("No game found to be scanned with ID '" + gameId + "'");
      }
    } catch (Exception e) {
      if (game != null) {
        LOG.error("Game scan for \"" + game.getGameDisplayName() + "\" (" + gameId + ") failed: " + e.getMessage(), e);
      }
      else {
        LOG.error("Game scan for game " + gameId + " failed: " + e.getMessage(), e);
      }
    }
    return game;
  }

  @Nullable
  public HighscoreMetadata scanScore(int gameId) {
    Game game = getGame(gameId);
    if (game != null) {
      return highscoreService.scanScore(game);
    }
    return null;
  }

  @SuppressWarnings("unused")
  public List<Game> getActiveGameInfos() {
    List<Integer> gameIdsFromPlaylists = this.pinUPConnector.getGameIdsFromPlaylists();
    List<Game> games = pinUPConnector.getGames();
    return games.stream().filter(g -> gameIdsFromPlaylists.contains(g.getId())).collect(Collectors.toList());
  }

  public Game getGameByFilename(String name) {
    Game game = this.pinUPConnector.getGameByFilename(name);
    if (game != null) {
      //this will ensure that a scanned table is fetched
      game = this.getGame(game.getId());
    }
    return game;
  }

  public Game getGameByName(String name) {
    Game game = this.pinUPConnector.getGameByName(name);
    if (game != null) {
      //this will ensure that a scanned table is fetched
      game = this.getGame(game.getId());
    }
    return game;
  }

  private void applyGameDetails(@NonNull Game game, boolean forceScan) {
    GameDetails gameDetails = gameDetailsRepository.findByPupId(game.getId());
    if (gameDetails == null || forceScan) {
      ScanResult scanResult = romService.scanGameFile(game);

      if (gameDetails == null) {
        gameDetails = new GameDetails();
      }

      gameDetails.setRomName(scanResult.getRom());
      gameDetails.setTableName(scanResult.getTableName());
      gameDetails.setNvOffset(scanResult.getNvOffset());
      gameDetails.setHsFileName(scanResult.getHsFileName());
      gameDetails.setPupId(game.getId());
      gameDetails.setAssets(StringUtils.join(scanResult.getAssets(), ","));
      gameDetails.setCreatedAt(new java.util.Date());
      gameDetails.setUpdatedAt(new java.util.Date());

      gameDetailsRepository.saveAndFlush(gameDetails);
      LOG.info("Created GameDetails for " + game.getGameDisplayName());
    }


    //use the script ROM name to check if it is an original or a mapping
    String originalRom = romService.getRomForAlias(gameDetails.getRomName());
    if (!StringUtils.isEmpty(originalRom)) {
      game.setRom(originalRom);
      game.setRomAlias(gameDetails.getRomName());
    }
    else {
      game.setRom(gameDetails.getRomName());
    }

    game.setNvOffset(gameDetails.getNvOffset());
    game.setHsFileName(gameDetails.getHsFileName());
    game.setTableName(gameDetails.getTableName());
    game.setExtTableId(gameDetails.getExtTableId());
    game.setExtTableVersionId(gameDetails.getExtTableVersionId());
    game.setPupPack(pupPackService.getPupPack(game));
    game.setIgnoredValidations(ValidationState.toIds(gameDetails.getIgnoredValidations()));
    game.setAltSoundAvailable(altSoundService.isAltSoundAvailable(game));
    game.setAltColorAvailable(altColorService.isAltColorAvailable(game));

    Optional<Highscore> highscore = this.highscoreService.getOrCreateHighscore(game);
    highscore.ifPresent(value -> game.setHighscoreType(value.getType() != null ? HighscoreType.valueOf(value.getType()) : null));

    //run validations at the end!!!
    game.setValidationState(gameValidator.validate(game));
  }

  public List<ValidationState> getRomValidations(Game game) {
    return gameValidator.validateRom(game);
  }

  //TODO mpf
  public boolean rename(PopperService popperService, Game game) {
    String gameFilename = game.getGameFileName();
    if (!gameFilename.endsWith(".vpx")) {
      gameFilename = gameFilename + ".vpx";
    }

    TableDetails original = pinUPConnector.getTableDetails(game.getId());
    Game originalGame = getGame(game.getId());
    if (original != null && originalGame != null) {
      if (!original.getGameDisplayName().equals(game.getGameDisplayName())) {
        original.setGameDisplayName(game.getGameDisplayName());
        pinUPConnector.saveTableDetails(game, original);
        LOG.info("Finished game display name renmaing to " + game.getGameDisplayName());
        return true;
      }
      else if (!original.getGameFileName().equals(gameFilename)) {
        String originalFileName = original.getGameFileName();
        original.setGameFileName(game.getGameFileName());
        pinUPConnector.saveTableDetails(game, original);

        //rename popper media
        String originalName = FilenameUtils.getBaseName(originalFileName);
        String newName = FilenameUtils.getBaseName(gameFilename);
        popperService.renameGameMedia(originalGame, originalName, newName);

        //rename additional files
        String name = FilenameUtils.getBaseName(gameFilename);
        FileUtils.rename(originalGame.getGameFile(), name);
        FileUtils.rename(originalGame.getDirectB2SFile(), name);
        FileUtils.rename(originalGame.getPOVFile(), name);
        FileUtils.rename(originalGame.getResFile(), name);
        LOG.info("Finished game file renaming to " + game.getGameFileName());
        return true;
      }
    }
    return false;
  }

  public Game save(Game game) throws Exception {
    GameDetails gameDetails = gameDetailsRepository.findByPupId(game.getId());
    String existingRom = String.valueOf(gameDetails.getRomName());
    boolean romChanged = !String.valueOf(game.getRom()).equalsIgnoreCase(existingRom);

    gameDetails.setRomName(game.getRom());
    gameDetails.setHsFileName(game.getHsFileName());
    gameDetails.setTableName(game.getTableName());
    gameDetails.setIgnoredValidations(ValidationState.toIdString(game.getIgnoredValidations()));
    gameDetails.setExtTableId(game.getExtTableId());
    gameDetails.setExtTableVersionId(game.getExtTableVersionId());
    gameDetailsRepository.saveAndFlush(gameDetails);

    Game original = getGame(game.getId());
    //TODO check rom name import vs. scan
    //check if there is mismatch in the ROM name, overwrite popper value
    if (original != null && !StringUtils.isEmpty(original.getRom()) && !StringUtils.isEmpty(game.getRom()) && !original.getRom().equals(game.getRom())) {
      pinUPConnector.updateRom(game, game.getRom());
    }

    LOG.info("Saved " + game);
    Game updated = getGame(game.getId());
    if (romChanged) {
      highscoreService.scanScore(updated);
      cardService.generateCard(updated, false);
    }
    return updated;
  }
}
