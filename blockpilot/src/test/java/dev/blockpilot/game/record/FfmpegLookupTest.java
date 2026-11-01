package dev.blockpilot.game.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.blockpilot.game.record.FfmpegLookup.Found;
import dev.blockpilot.game.record.FfmpegLookup.Platform;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FfmpegLookupTest {
    @Test
    void theConfiguredPathWins() {
        Found found = FfmpegLookup.find("/custom/ffmpeg", Platform.MAC, Map.of("PATH", "/usr/bin"),
            Set.of("/custom/ffmpeg", "/usr/bin/ffmpeg")::contains);
        assertEquals("/custom/ffmpeg", found.ffmpeg());
    }

    @Test
    void aConfiguredPathThatIsMissingIsNotReplacedByAnother() {
        Found found = FfmpegLookup.find("/wrong/ffmpeg", Platform.MAC, Map.of("PATH", "/usr/bin"),
            Set.of("/usr/bin/ffmpeg")::contains);
        assertNull(found.ffmpeg());
        assertEquals(List.of("/wrong/ffmpeg"), found.searched());
    }

    @Test
    void thePathComesInOrderBeforeTheUsualFolders() {
        Found found = FfmpegLookup.find("", Platform.MAC, Map.of("PATH", "/first:/second"),
            Set.of("/second/ffmpeg", "/opt/homebrew/bin/ffmpeg")::contains);
        assertEquals("/second/ffmpeg", found.ffmpeg());
        assertEquals(List.of("/first", "/second"), found.searched());
    }

    @Test
    void homebrewIsFoundWithTheBarePathOfALauncher() {
        Found found = FfmpegLookup.find(null, Platform.MAC, Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin"),
            Set.of("/opt/homebrew/bin/ffmpeg")::contains);
        assertEquals("/opt/homebrew/bin/ffmpeg", found.ffmpeg());
    }

    @Test
    void linuxLooksInItsUsualFolders() {
        Found found = FfmpegLookup.find(null, Platform.LINUX, Map.of(), Set.of("/snap/bin/ffmpeg")::contains);
        assertEquals("/snap/bin/ffmpeg", found.ffmpeg());
        assertEquals(List.of("/usr/bin", "/usr/local/bin", "/snap/bin"), found.searched());
    }

    @Test
    void windowsReadsItsVariablesInAnyCaseAndLooksForTheExe() {
        Map<String, String> environment = Map.of("Path", "C:\\Tools\\", "LOCALAPPDATA", "C:\\Users\\a\\AppData\\Local",
            "USERPROFILE", "C:\\Users\\a", "ProgramData", "C:\\ProgramData");
        Found found = FfmpegLookup.find(null, Platform.WINDOWS, environment,
            Set.of("C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe")::contains);
        assertEquals("C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe", found.ffmpeg());
        assertEquals(List.of("C:\\Tools\\", "C:\\Users\\a\\AppData\\Local\\Microsoft\\WinGet\\Links",
            "C:\\Users\\a\\scoop\\shims", "C:\\ProgramData\\chocolatey\\bin"), found.searched());
    }

    @Test
    void aMissListsEveryPlaceSearchedOnce() {
        Found found = FfmpegLookup.find(null, Platform.MAC, Map.of("PATH", "/usr/local/bin::/bin"), path -> false);
        assertNull(found.ffmpeg());
        assertEquals(List.of("/usr/local/bin", "/bin", "/opt/homebrew/bin", "/opt/local/bin"), found.searched());
    }
}
