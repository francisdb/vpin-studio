package de.mephisto.vpin.ui.tables;

import de.mephisto.vpin.restclient.jobs.JobType;
import de.mephisto.vpin.restclient.representations.GameRepresentation;
import de.mephisto.vpin.ui.NavigationController;
import de.mephisto.vpin.ui.Studio;
import de.mephisto.vpin.ui.StudioFXController;
import de.mephisto.vpin.ui.archiving.RepositoryController;
import de.mephisto.vpin.ui.archiving.RepositorySidebarController;
import de.mephisto.vpin.ui.events.EventManager;
import de.mephisto.vpin.ui.events.JobFinishedEvent;
import de.mephisto.vpin.ui.events.StudioEventListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;

public class TablesController implements Initializable, StudioFXController, StudioEventListener {
  private final static Logger LOG = LoggerFactory.getLogger(TablesController.class);

  private TableOverviewController tableOverviewController;
  private RepositoryController repositoryController;

  @FXML
  private Parent root;

  @FXML
  private TabPane tabPane;

  @FXML
  private Tab tablesTab;

  @FXML
  private Tab tableRepositoryTab;

  @FXML
  private TablesSidebarController tablesSideBarController; //fxml magic! Not unused

  @FXML
  private RepositorySidebarController repositorySideBarController; //fxml magic! Not unused

  @FXML
  private StackPane editorRootStack;

  @Override
  public void onViewActivated() {
    refreshTabSelection(tabPane.getSelectionModel().getSelectedIndex());
  }

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    NavigationController.setInitialController("scene-tables.fxml", this, root);

    NavigationController.setBreadCrumb(Arrays.asList("Players", "Build-In Players"));
    EventManager.getInstance().addListener(this);

    try {
      FXMLLoader loader = new FXMLLoader(TableOverviewController.class.getResource("scene-tables-overview.fxml"));
      Parent tablesRoot = loader.load();
      tableOverviewController = loader.getController();
      tableOverviewController.setRootController(this);
      tablesSideBarController.setTablesController(tableOverviewController);
      tablesTab.setContent(tablesRoot);
    } catch (IOException e) {
      LOG.error("failed to load table overview: " + e.getMessage(), e);
    }

    try {
      FXMLLoader loader = new FXMLLoader(RepositoryController.class.getResource("scene-repository.fxml"));
      Parent repositoryRoot = loader.load();
      repositoryController = loader.getController();
      repositoryController.setRootController(this);
      tableRepositoryTab.setContent(repositoryRoot);
    } catch (IOException e) {
      LOG.error("failed to load table overview: " + e.getMessage(), e);
    }


    tabPane.getSelectionModel().selectedIndexProperty().addListener((observableValue, number, t1) -> {
      refreshTabSelection(t1);
    });

    tablesSideBarController.setVisible(true);
    repositorySideBarController.setVisible(false);
  }

  private void refreshTabSelection(Number t1) {
    Platform.runLater(() -> {
      if (t1.intValue() == 0) {
        NavigationController.setBreadCrumb(Arrays.asList("Tables"));
        tablesSideBarController.setVisible(true);
        repositorySideBarController.setVisible(false);
        tableOverviewController.initSelection();
      }
      else {
        NavigationController.setBreadCrumb(Arrays.asList("Table Repository"));
        tablesSideBarController.setVisible(false);
        repositorySideBarController.setVisible(true);
        repositoryController.initSelection();
      }
    });
  }

  public TablesSidebarController getTablesSideBarController() {
    return tablesSideBarController;
  }

  public RepositorySidebarController getRepositorySideBarController() {
    return repositorySideBarController;
  }

  public TableOverviewController getTableOverviewController() {
    return tableOverviewController;
  }

  @Override
  public void jobFinished(@NonNull JobFinishedEvent event) {
    JobType jobType = event.getJobType();
    if (jobType.equals(JobType.TABLE_BACKUP) || jobType.equals(JobType.ARCHIVE_INSTALL)) {
      Platform.runLater(() -> {
        repositoryController.doReload();
      });
    }
    else if (jobType.equals(JobType.PUP_INSTALL) || jobType.equals(JobType.ALTSOUND_INSTALL) || jobType.equals(JobType.ALTCOLOR_INSTALL)) {
      Platform.runLater(() -> {
        if (event.getGameId() > 0) {
          GameRepresentation game = Studio.client.getGameService().getGame(event.getGameId());
          String rom = null;
          if (game != null) {
            rom = game.getRom();
          }
          EventManager.getInstance().notifyTableChange(event.getGameId(), rom);
        }
        else {
          this.tableOverviewController.onReload();
        }
      });
    }
    else if (jobType.equals(JobType.POV_INSTALL)
        || jobType.equals(JobType.POPPER_MEDIA_INSTALL)
        || jobType.equals(JobType.DIRECTB2S_INSTALL)
        || jobType.equals(JobType.TABLE_IMPORT)) {
      Platform.runLater(() -> {
        if (event.getGameId() > 0) {
          EventManager.getInstance().notifyTableChange(event.getGameId(), null);
        }
        else {
          this.tableOverviewController.onReload();
        }
      });
    }
  }

  @Override
  public void tableChanged(int id, String rom) {
    if (StringUtils.isEmpty(rom)) {
      if (id > 0) {
        this.tableOverviewController.reload(id);
      }
    }
    else {
      this.tableOverviewController.reload(rom);
    }
  }

  public StackPane getEditorRootStack() {
    return editorRootStack;
  }

  @Override
  public void preferencesChanged() {
    Platform.runLater(() -> {
      this.tableOverviewController.onReload();
    });
  }
}
