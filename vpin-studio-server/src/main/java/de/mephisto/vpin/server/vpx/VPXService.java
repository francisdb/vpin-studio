package de.mephisto.vpin.server.vpx;

import de.mephisto.vpin.commons.POV;
import de.mephisto.vpin.commons.utils.FileUtils;
import de.mephisto.vpin.server.VPinStudioException;
import de.mephisto.vpin.server.games.Game;
import de.mephisto.vpin.server.games.GameService;
import de.mephisto.vpin.server.system.SystemService;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class VPXService {
  private final static Logger LOG = LoggerFactory.getLogger(VPXService.class);

  @Autowired
  private GameService gameService;

  @Autowired
  private SystemService systemService;

  @Autowired
  private VPXCommandLineService vpxCommandLineService;

  public POV getPOV(int gameId) {
    try {
      Game game = gameService.getGame(gameId);
      if (game != null) {
        File povFile = game.getPOVFile();
        if (povFile.exists()) {
          return POVParser.parse(povFile, gameId);
        }
      }
      return null;
    } catch (VPinStudioException e) {
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  public boolean savePOVPreference(int gameId, Map<String, Object> values) {
    POV pov = getPOV(gameId);
    if (pov != null) {
      BeanWrapper bean = new BeanWrapperImpl(pov);
      String property = (String) values.get("property");
      Object value = values.get("value");
      bean.setPropertyValue(property, value);
      save(pov);
      return true;
    }
    return false;
  }

  @Nullable
  public POV create(int gameId) {
    Game game = gameService.getGame(gameId);
    if (game != null) {
      if (game.getPOVFile().exists()) {
        if (!game.getPOVFile().delete()) {
          throw new UnsupportedOperationException("Failed to delete " + game.getPOVFile().getAbsolutePath());
        }
      }

      if (!game.getPOVFile().exists()) {
        createPOV(game.getId());
        return getPOV(game.getId());
      }
    }
    return null;
  }

  public POV save(POV pov) {
    try {
      Game game = gameService.getGame(pov.getGameId());
      if (game != null) {
        if (!game.getPOVFile().exists()) {
          createPOV(game.getId());
          return getPOV(game.getId());
        }

        POVSerializer.serialize(pov, game);
      }
      return pov;
    } catch (VPinStudioException e) {
      throw new ResponseStatusException(INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  public POV createPOV(int gameId) {
    Game game = gameService.getGame(gameId);
    if (game != null) {
      try {
        File target = vpxCommandLineService.export(game, "-Pov", "pov");
        if (target.exists()) {
          return getPOV(gameId);
        }
      } catch (Exception e) {
        LOG.error("Error executing shutdown: " + e.getMessage(), e);
      }
    }
    LOG.error("No game found for pov creation with id " + gameId);
    return null;
  }

  public String getScript(int gameId) {
    Game game = gameService.getGame(gameId);
    if (game != null) {
      File target = vpxCommandLineService.export(game, "-ExtractVBS", "vbs");
      if (target.exists()) {
        try {
          LOG.info("Reading vbs file " + target.getAbsolutePath() + " (" + FileUtils.readableFileSize(target.length()) + ")");
          Path filePath = Path.of(target.toURI());
          return Files.readString(filePath);
        } catch (IOException e) {
          LOG.error("Failed to read " + target.getAbsolutePath() + ": " + e.getMessage(), e);
        } finally {
          if (!target.delete()) {
            LOG.error("Failed to clean up vbs file " + target.getAbsolutePath());
          }
        }
      }

    }
    LOG.error("No game found for script extraction, id " + gameId);
    return null;
  }

  public String getSources(int gameId) {
    Game game = gameService.getGame(gameId);
    if (game != null) {
      File gameFile = game.getGameFile();
      if (gameFile.exists()) {
        String sources = VPXUtil.readScript(gameFile);
        return Base64.getEncoder().encodeToString(sources.getBytes());
      }
    }
    LOG.error("No game found for table sources, id " + gameId);
    return null;
  }

  public boolean saveSources(int gameId, String base64Source) {
    Game game = gameService.getGame(gameId);
    if (game != null) {
      File gameFile = game.getGameFile();
      if (gameFile.exists()) {
        try {
          byte[] decoded = Base64.getDecoder().decode(base64Source);
          VPXUtil.writeGameData(gameFile, decoded);
          LOG.info("Written table sources " + gameFile.getAbsolutePath());
          return true;
        } catch (IOException e) {
          //already logged
        }
      }
    }
    return false;
  }

  public boolean delete(int id) {
    Game game = gameService.getGame(id);
    if (game != null) {
      File povFile = game.getPOVFile();
      if (povFile.exists()) {
        LOG.info("Deleting " + povFile.getAbsolutePath());
        return povFile.delete();
      }
      else {
        LOG.info("POV file " + povFile.getAbsolutePath() + " does not exist for deletion");
      }
    }
    LOG.error("No game found for pov creation with id " + id);
    return false;
  }

  public boolean play(int id) {
    Game game = gameService.getGame(id);
    if (game != null) {
      systemService.killPopper();
      return vpxCommandLineService.execute(game, "-Play");
    }
    return false;
  }
}
