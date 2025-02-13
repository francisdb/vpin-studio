package de.mephisto.vpin.server.popper;

import de.mephisto.vpin.restclient.popper.EmulatorType;
import de.mephisto.vpin.commons.fx.UIDefaults;
import de.mephisto.vpin.restclient.PopperCustomOptions;
import de.mephisto.vpin.restclient.SystemData;
import de.mephisto.vpin.restclient.TableManagerSettings;
import de.mephisto.vpin.restclient.jobs.JobExecutionResult;
import de.mephisto.vpin.restclient.jobs.JobExecutionResultFactory;
import de.mephisto.vpin.restclient.popper.PinUPControl;
import de.mephisto.vpin.restclient.popper.PinUPControls;
import de.mephisto.vpin.restclient.popper.PopperScreen;
import de.mephisto.vpin.restclient.popper.TableDetails;
import de.mephisto.vpin.server.games.Game;
import de.mephisto.vpin.server.games.GameService;
import de.mephisto.vpin.server.system.SystemService;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PopperService implements InitializingBean {
  private final static Logger LOG = LoggerFactory.getLogger(PopperService.class);

  private final List<PopperStatusChangeListener> listeners = new ArrayList<>();

  @Autowired
  private SystemService systemService;

  @Autowired
  private GameService gameService;

  @Autowired
  private PinUPConnector pinUPConnector;

  public PinUPControl getPinUPControlFor(PopperScreen screen) {
    return pinUPConnector.getPinUPControlFor(screen);
  }

  public PinUPControls getPinUPControls() {
    return pinUPConnector.getControls();
  }

  @SuppressWarnings("unused")
  public void addPopperStatusChangeListener(PopperStatusChangeListener listener) {
    this.listeners.add(listener);
  }

  public SystemData getImportTables() {
    SystemData list = new SystemData();
    File vpxTablesFolder = systemService.getVPXTablesFolder();
    File[] files = vpxTablesFolder.listFiles((dir, name) -> name.endsWith(".vpx"));
    if (files != null) {
      List<Game> games = pinUPConnector.getGames();
      List<String> filesNames = games.stream().map(Game::getGameFileName).collect(Collectors.toList());
      for (File file : files) {
        if (!filesNames.contains(file.getName())) {
          list.getItems().add(file.getName());
        }
      }
    }
    return list;
  }

  public JobExecutionResult importTables(SystemData resourceList) {
    List<String> items = resourceList.getItems();
    int count = 0;
    for (String item : items) {
      File tableFile = new File(systemService.getVPXTablesFolder(), item);
      if (tableFile.exists()) {
        int result = importVPXGame(tableFile, true, -1);
        if (result > 0) {
          gameService.scanGame(result);
          count++;
        }
      }
    }

    return JobExecutionResultFactory.ok("Imported " + count + " tables", -1);
  }

  public int importVPXGame(File file, boolean importToPopper, int playListId) {
    if (importToPopper) {
      int gameId = pinUPConnector.importGame(file);
      if (gameId >= 0 && playListId >= 0) {
        pinUPConnector.addToPlaylist(playListId, gameId);
      }
      return gameId;
    }
    return -1;
  }

  public void notifyTableStatusChange(final Game game, final boolean started) {
    TableStatusChangedEvent event = () -> game;
    for (PopperStatusChangeListener listener : this.listeners) {
      if (started) {
        listener.tableLaunched(event);
      }
      else {
        listener.tableExited(event);
      }
    }
  }

  public boolean isPinUPRunning() {
    Optional<ProcessHandle> pinUP = ProcessHandle.allProcesses().filter(p -> p.info().command().isPresent() && p.info().command().get().contains("PinUP")).findFirst();
    return pinUP.isPresent();
  }

  public boolean terminate() {
    boolean b = systemService.killPopper();
    for (PopperStatusChangeListener listener : listeners) {
      listener.popperExited();
    }
    return b;
  }

  public void notifyPopperLaunch() {
    for (PopperStatusChangeListener listener : listeners) {
      listener.popperLaunched();
    }
  }

  public void notifyPopperRestart() {
    for (PopperStatusChangeListener listener : listeners) {
      listener.popperRestarted();
    }
  }

  public void notifyPopperExit() {
    for (PopperStatusChangeListener listener : listeners) {
      listener.popperExited();
    }
  }

  public TableDetails getTableDetails(int gameId) {
    return pinUPConnector.getTableDetails(gameId);
  }

  public TableDetails saveTableDetails(TableDetails tableDetails, int gameId) {
    Game game = pinUPConnector.getGame(gameId);
    pinUPConnector.saveTableDetails(game, tableDetails);
    return tableDetails;
  }

  public boolean restart() {
    systemService.restartPopper();
    return true;
  }

  public void augmentWheel(Game game, String badge) {
    GameMediaItem gameMediaItem = game.getGameMedia().getDefaultMediaItem(PopperScreen.Wheel);
    if (gameMediaItem != null) {
      File wheelIcon = gameMediaItem.getFile();
      WheelAugmenter augmenter = new WheelAugmenter(wheelIcon);

      File badgeFile = systemService.getBagdeFile(badge);
      if (badgeFile.exists()) {
        augmenter.augment(badgeFile);
      }
    }
  }

  public void deAugmentWheel(Game game) {
    GameMediaItem gameMediaItem = game.getGameMedia().getDefaultMediaItem(PopperScreen.Wheel);
    if (gameMediaItem != null) {
      File wheelIcon = gameMediaItem.getFile();
      WheelAugmenter augmenter = new WheelAugmenter(wheelIcon);
      augmenter.deAugment();
    }
  }

  public boolean saveArchiveManager(TableManagerSettings archiveManagerDescriptor) {
    if (archiveManagerDescriptor.getPlaylistId() != -1) {
      pinUPConnector.enablePCGameEmulator();
      File file = systemService.getVPinStudioMenuExe();
      int newGameId = pinUPConnector.importGame(EmulatorType.PC_GAMES, UIDefaults.MANAGER_TITLE, file.getAbsolutePath(),
          UIDefaults.MANAGER_TITLE, UIDefaults.MANAGER_TITLE);
      pinUPConnector.addToPlaylist(archiveManagerDescriptor.getPlaylistId(), newGameId);
    }
    else {
      File file = systemService.getVPinStudioMenuExe();
      pinUPConnector.deleteGame(file.getAbsolutePath());
    }
    return true;
  }

  @NonNull
  public TableManagerSettings getArchiveManagerDescriptor() {
    TableManagerSettings descriptor = new TableManagerSettings();
    File file = systemService.getVPinStudioMenuExe();
    Game game = pinUPConnector.getGameByFilename(file.getAbsolutePath());
    if (game != null) {
      descriptor.setPlaylistId(pinUPConnector.getPlayListForGame(game.getId()).getId());
    }
    return descriptor;
  }

  public void cloneGameMedia(Game original, Game clone) {
    PopperScreen[] values = PopperScreen.values();
    for (PopperScreen originalScreenValue : values) {
      try {
        List<GameMediaItem> gameMediaItems = original.getGameMedia().getMediaItems(originalScreenValue);
        for (GameMediaItem gameMediaItem : gameMediaItems) {
          if (gameMediaItem.getFile().exists()) {
            File mediaFile = gameMediaItem.getFile();
            String suffix = FilenameUtils.getExtension(mediaFile.getName());
            String originalName = FilenameUtils.getBaseName(mediaFile.getName());
            String targetName = FilenameUtils.getBaseName(clone.getGameFileName());
            String name = originalName.replace(FilenameUtils.getBaseName(original.getGameFileName()), targetName);

            File cloneTarget = new File(clone.getPinUPMediaFolder(originalScreenValue), name + "." + suffix);
            if (cloneTarget.exists()) {
              cloneTarget.delete();
            }

            FileUtils.copyFile(mediaFile, cloneTarget);
            LOG.info("Cloned PinUP Popper media: " + mediaFile.getAbsolutePath() + " to " + cloneTarget.getAbsolutePath());
          }
        }
      } catch (IOException e) {
        LOG.info("Failed to clone popper media: " + e.getMessage(), e);
      }
    }
  }

  public void renameGameMedia(Game game, String oldName, String newBaseName) {
    PopperScreen[] values = PopperScreen.values();
    for (PopperScreen originalScreenValue : values) {

      List<GameMediaItem> gameMediaItems = game.getGameMedia().getMediaItems(originalScreenValue);
      for (GameMediaItem gameMediaItem : gameMediaItems) {
        File gameMediaFile = gameMediaItem.getFile();
        if (gameMediaFile.exists()) {
          String name = gameMediaFile.getName();
          String newName = name.replace(oldName, newBaseName);
          File target = new File(gameMediaFile.getParentFile(), newName);
          if (gameMediaFile.renameTo(target)) {
            LOG.info("Renamed PinUP Popper media from " + gameMediaFile.getAbsolutePath() + " to " + target.getAbsolutePath());
          }
          else {
            LOG.warn("Renaming PinUP Popper media from " + gameMediaFile.getAbsolutePath() + " to " + target.getName() + " failed.");
          }
        }
      }
    }
  }

  public PopperCustomOptions getCustomOptions() {
    return pinUPConnector.getCustomOptions();
  }

  public PopperCustomOptions saveCustomOptions(PopperCustomOptions options) throws SQLException {
    pinUPConnector.updateCustomOptions(options);
    return options;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    Thread shutdownHook = new Thread(this::notifyPopperExit);
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }
}
