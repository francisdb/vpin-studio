package de.mephisto.vpin.restclient.representations;

import de.mephisto.vpin.restclient.popper.PopperScreen;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameMediaRepresentation {
  private Map<String, List<GameMediaItemRepresentation>> media = new HashMap<>();

  public Map<String, List<GameMediaItemRepresentation>> getMedia() {
    return media;
  }

  public void setMedia(Map<String, List<GameMediaItemRepresentation>> media) {
    this.media = media;
  }

  public List<GameMediaItemRepresentation> getMediaItems(PopperScreen screen) {
    return this.media.get(screen.name());
  }

  @Nullable
  public GameMediaItemRepresentation getDefaultMediaItem(@NonNull PopperScreen screen) {
    if (!media.containsKey(screen.name())) {
      return null;
    }

    List<GameMediaItemRepresentation> gameMediaItems = media.get(screen.name());
    if (media.isEmpty()) {
      return null;
    }

    GameMediaItemRepresentation fallback = null;
    for (GameMediaItemRepresentation gameMediaItem : gameMediaItems) {
      fallback = gameMediaItem;
      if (gameMediaItem.getName().contains("[SCREEN")) {
        return gameMediaItem;
      }
    }

    return fallback;
  }
}
