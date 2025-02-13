package de.mephisto.vpin.server.listeners;

import de.mephisto.vpin.server.competitions.Competition;
import de.mephisto.vpin.server.competitions.CompetitionChangeListener;
import de.mephisto.vpin.server.competitions.CompetitionService;
import de.mephisto.vpin.server.competitions.ScoreSummary;
import de.mephisto.vpin.server.games.Game;
import de.mephisto.vpin.server.games.GameService;
import de.mephisto.vpin.server.players.Player;
import de.mephisto.vpin.server.popper.PopperService;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

abstract public class DefaultCompetitionChangeListener implements CompetitionChangeListener {

  @Override
  public void competitionStarted(@NonNull Competition competition) {

  }


  @Override
  public void competitionCreated(@NotNull Competition competition) {

  }

  @Override
  public void competitionChanged(@NotNull Competition competition) {

  }

  @Override
  public void competitionFinished(@NonNull Competition competition, @Nullable Player winner, @NonNull ScoreSummary scoreSummary) {

  }

  @Override
  public void competitionDeleted(@NonNull Competition competition) {
  }


  /**
   * Checks if there are any augmented wheel icons that do not belong
   * to any competition anymore.
   */
  protected void runCheckedDeAugmentation(CompetitionService competitionService, GameService gameService, PopperService popperService) {
    List<Integer> competedGameIds = competitionService.getActiveCompetitions().stream().map(Competition::getGameId).collect(Collectors.toList());
    List<Game> games = gameService.getGames();
    for (Game game : games) {
      if (!competedGameIds.contains(game.getId())) {
        popperService.deAugmentWheel(game);
      }
    }
  }
}
