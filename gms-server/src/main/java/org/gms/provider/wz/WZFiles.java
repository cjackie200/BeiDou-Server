package org.gms.provider.wz;

import org.gms.manager.ServerManager;
import org.gms.property.ServiceProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public enum WZFiles {
    QUEST("Quest"),
    ETC("Etc"),
    ITEM("Item"),
    CHARACTER("Character"),
    STRING("String"),
    LIST("List"),
    MOB("Mob"),
    MAP("Map"),
    NPC("Npc"),
    REACTOR("Reactor"),
    SKILL("Skill"),
    SOUND("Sound"),
    UI("UI");

    private final String fileName;
    public static final String DIRECTORY = "wz";

    WZFiles(String name) {
        this.fileName = name + ".wz";
    }

    public Path getFile() {
        // 优先取语言文件夹，没有则取wz
        Path wzPath = Path.of(DIRECTORY, fileName);
        ServiceProperty serviceProperty = ServerManager.getApplicationContext().getBean(ServiceProperty.class);
        Path langPath = Path.of(DIRECTORY + "-" + serviceProperty.getLanguage(), fileName);

        // 语言目录存在且有实际文件时才使用，否则回退到 wz 基础目录
        if (Files.exists(langPath) && !isEmptyDir(langPath)) {
            return langPath;
        }
        return wzPath;
    }

    private static boolean isEmptyDir(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }

    public String getFilePath() {
        return getFile().toString();
    }
}
