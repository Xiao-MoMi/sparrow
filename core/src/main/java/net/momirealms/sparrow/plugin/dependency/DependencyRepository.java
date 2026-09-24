package net.momirealms.sparrow.plugin.dependency;

import net.momirealms.sparrow.plugin.dependency.exception.DependencyDownloadException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public enum DependencyRepository {

    // Maven 中央仓库.
    MAVEN("maven", "https://repo1.maven.org/maven2/") {
        @Override
        protected URLConnection openConnection(Dependency dependency) throws IOException {
            URLConnection connection = super.openConnection(dependency);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            return connection;
        }
    },
    
    // Google 镜像仓库.
    GOOGLE("maven", "https://maven-central.storage-download.googleapis.com/maven2/") {
        @Override
        protected URLConnection openConnection(Dependency dependency) throws IOException {
            URLConnection connection = super.openConnection(dependency);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            return connection;
        }
    },
    
    // 阿里云 镜像仓库.
    ALIYUN("maven", "https://maven.aliyun.com/repository/public/");

    private final String url;
    private final String id;

    /**
     * @param id  仓库的标识符 (例如 "maven").
     * @param url 仓库的基础 URL 地址.
     */
    DependencyRepository(String id, String url) {
        this.url = url;
        this.id = id;
    }

    /**
     * 获取仓库的标识符.
     *
     * @return 仓库的标识符字符串.
     */
    public String id() {
        return id;
    }

    /**
     * 获取该仓库的基础 URL 地址.
     *
     * @return 仓库的基础 URL.
     */
    public String getUrl() {
        return this.url;
    }

    /**
     * 根据指定的标识符获取匹配的依赖仓库列表.
     *
     * @param id 要查询的仓库标识符.
     * @return 匹配该标识符的仓库列表.
     */
    public static List<DependencyRepository> getByID(String id) {
        ArrayList<DependencyRepository> repositories = new ArrayList<>();
        for (DependencyRepository repository : values()) {
            if (id.equals(repository.id)) {
                repositories.add(repository);
            }
        }
        // 中国大陆优先使用国内阿里云镜像
        if (id.equals("maven") && Locale.getDefault() == Locale.SIMPLIFIED_CHINESE) {
            Collections.reverse(repositories);
        }
        return repositories;
    }

    /**
     * 打开到指定依赖项在当前仓库中的网络连接.
     *
     * @param dependency 需要建立连接的依赖项.
     * @return 建立的网络连接 URLConnection 对象.
     * @throws IOException 如果解析 URL 或建立连接失败.
     */
    protected URLConnection openConnection(Dependency dependency) throws IOException {
        @SuppressWarnings("deprecation") // 1.20
        URL dependencyUrl = new URL(this.url + dependency.mavenPath());
        return dependencyUrl.openConnection();
    }

    /**
     * 建立网络连接并直接下载依赖项的原始字节数据.
     *
     * @param dependency 要下载的依赖项.
     * @return 下载得到的原始字节数组.
     * @throws DependencyDownloadException 如果在连接, 读取流或流为空时发生任何异常.
     */
    public byte[] download(Dependency dependency) throws DependencyDownloadException {
        try {
            URLConnection connection = openConnection(dependency);
            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length == 0) {
                    throw new DependencyDownloadException("Empty stream");
                }
                return bytes;
            }
        } catch (Exception e) {
            throw new DependencyDownloadException(e);
        }
    }

    /**
     * 下载指定的依赖项并将其保存到本地文件中.
     *
     * @param dependency 要下载的依赖项.
     * @param file       保存下载数据的本地目标文件路径.
     * @throws DependencyDownloadException 如果在创建目录, 下载数据或写入文件时发生 I/O 异常.
     */
    public void download(Dependency dependency, Path file) throws DependencyDownloadException {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, download(dependency));
        } catch (IOException e) {
            throw new DependencyDownloadException(e);
        }
    }
}
