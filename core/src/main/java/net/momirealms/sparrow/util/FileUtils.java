package net.momirealms.sparrow.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public final class FileUtils {
    private FileUtils() {}

    /**
     * 检查给定的路径字符串是否为绝对路径.
     * 支持 Unix 风格 (以 '/' 开头) 和 Windows 风格 (如 'C:\' 开头).
     *
     * @param path 需要检查的路径字符串
     * @return 如果是绝对路径则返回 true, 否则返回 false
     */
    public static boolean isAbsolute(final String path) {
        return path.startsWith("/") || path.matches("^[A-Za-z]:\\\\.*");
    }

    /**
     * 获取文件的扩展名.
     * 如果文件没有扩展名, 则返回空字符串.
     *
     * @param path 目标文件路径
     * @return 文件的扩展名 (不包含 '.'), 如果没有扩展名则返回空字符串
     */
    public static String getExtension(Path path) {
        final String name = path.getFileName().toString();
        int index = name.lastIndexOf('.');
        if (index == -1) {
            return "";
        } else {
            return name.substring(index + 1);
        }
    }

    /**
     * 获取去除扩展名后的路径字符串.
     * 如果路径中不包含 '.', 则返回原路径.
     *
     * @param path 原始路径字符串
     * @return 去除扩展名后的路径字符串
     */
    public static String pathWithoutExtension(String path) {
        int i = path.lastIndexOf('.');
        return i == -1 ? path : path.substring(0, i);
    }

    /**
     * 安全地创建多级目录.
     * 如果目录已经存在, 将获取其真实路径然后再进行创建操作, 以避免潜在的符号链接问题.
     *
     * @param path 需要创建的目录路径
     * @throws IOException 如果创建目录过程中发生 I/O 异常
     */
    public static void createDirectoriesSafe(Path path) throws IOException {
        Files.createDirectories(Files.exists(path) ? path.toRealPath() : path);
    }

    /**
     * 递归删除指定的目录及其包含的所有文件和子目录.
     * 如果目录不存在, 则直接返回.
     *
     * @param folder 需要删除的目录路径
     * @throws IOException 如果删除文件或目录过程中发生 I/O 异常
     */
    public static void deleteDirectory(Path folder) throws IOException {
        FileUtils.deleteDirectory(folder, path -> true);
    }

    /**
     * 根据条件递归删除指定的目录及其包含的所有文件和子目录.
     * 如果目录不存在, 则直接返回.
     *
     * @param folder 需要删除的目录路径
     * @param filter 过滤器
     * @throws IOException 如果删除文件或目录过程中发生 I/O 异常
     */
    public static void deleteDirectory(Path folder, Function<Path, Boolean> filter) throws IOException {
        if (!Files.exists(folder)) return;
        try (Stream<Path> walk = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            if (filter.apply(path)) {
                                Files.delete(path);
                            }
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    });
        }
    }

    /**
     * 清理指定目录下的所有 .jar 文件.
     * 该方法不会递归清理子目录中的文件.
     *
     * @param directory 要清理的目录路径
     * @throws IOException 如果遍历或删除文件时发生 I/O 异常
     */
    private static void cleanDirectoryJars(Path directory) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".jar")) {
                    Files.delete(file);
                }
            }
        }
    }

    /**
     * 递归遍历指定文件夹, 获取所有 .yml 格式的配置文件路径.
     * 如果目标文件夹不存在, 将返回一个空列表.
     * 该操作使用并行流以提高遍历效率.
     *
     * @param configFolder 需要遍历的配置文件夹路径
     * @return 包含所有 .yml 文件路径的列表
     * @throws RuntimeException 如果遍历目录时发生 I/O 异常
     */
    public static List<Path> getYmlConfigsDeeply(Path configFolder) {
        if (!Files.exists(configFolder)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(configFolder, FileVisitOption.FOLLOW_LINKS)) {
            return stream.parallel()
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yml"))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse directory: " + configFolder, e);
        }
    }

    /**
     * 检查给定文件是否为 .json 文件.
     *
     * @param filePath 需要检查的文件路径
     * @return 如果是 .json 文件则返回 true, 否则返回 false
     */
    public static boolean isJsonFile(Path filePath) {
        return filePath.getFileName().toString().endsWith(".json");
    }

    /**
     * 检查给定文件是否为 .mcmeta 文件.
     *
     * @param filePath 需要检查的文件路径
     * @return 如果是 .mcmeta 文件则返回 true, 否则返回 false
     */
    public static boolean isMcMetaFile(Path filePath) {
        return filePath.getFileName().toString().endsWith(".mcmeta");
    }

    /**
     * 检查给定文件是否为 .png 文件.
     *
     * @param filePath 需要检查的文件路径
     * @return 如果是 .png 文件则返回 true, 否则返回 false
     */
    public static boolean isPngFile(Path filePath) {
        return filePath.getFileName().toString().endsWith(".png");
    }

    /**
     * 检查给定文件是否为 .ogg 文件.
     *
     * @param filePath 需要检查的文件路径
     * @return 如果是 .ogg 文件则返回 true, 否则返回 false
     */
    public static boolean isOggFile(Path filePath) {
        return filePath.getFileName().toString().endsWith(".ogg");
    }
}
