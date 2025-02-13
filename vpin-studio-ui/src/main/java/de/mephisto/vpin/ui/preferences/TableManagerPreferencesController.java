package de.mephisto.vpin.ui.preferences;

import de.mephisto.vpin.commons.utils.WidgetFactory;
import de.mephisto.vpin.restclient.TableManagerSettings;
import de.mephisto.vpin.restclient.representations.PlaylistRepresentation;
import de.mephisto.vpin.ui.Studio;
import de.mephisto.vpin.ui.util.Dialogs;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static de.mephisto.vpin.ui.Studio.client;

public class TableManagerPreferencesController implements Initializable {

  @FXML
  private ComboBox<PlaylistRepresentation> playlistCombo;

  private TableManagerSettings archiveManagerSettings;

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    List<PlaylistRepresentation> playlists = new ArrayList<>(client.getPlaylistsService().getStaticPlaylists());
    playlists.add(0, null);
    ObservableList<PlaylistRepresentation> data = FXCollections.observableList(playlists);
    this.playlistCombo.setItems(data);


    archiveManagerSettings = client.getTableManagerService().getTableManagerSettings();
    if (archiveManagerSettings.getPlaylistId() != -1) {
      for (PlaylistRepresentation playlist : playlists) {
        if (playlist != null && playlist.getId() == archiveManagerSettings.getPlaylistId()) {
          this.playlistCombo.getSelectionModel().select(playlist);
        }
      }
    }

    this.playlistCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
      if (client.getPinUPPopperService().isPinUPPopperRunning()) {
        if (Dialogs.openPopperRunningWarning(Studio.stage)) {
          updateInstallation(newValue);
        }
      }
      else {
        updateInstallation(newValue);
      }
    });
  }

  private void updateInstallation(PlaylistRepresentation playlist) {
    archiveManagerSettings.setPlaylistId(playlist == null ? -1 : playlist.getId());

    try {
      client.getTableManagerService().saveTableManagerSettings(archiveManagerSettings);
    } catch (Exception e) {
      WidgetFactory.showAlert(Studio.stage, "Failed to update archive manager: " + e.getMessage());
    }
  }
}
