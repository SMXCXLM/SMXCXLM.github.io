package com.jsh.plugin.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件启动时自动建表，保证安装插件即可用，不需要手工执行 SQL。
 * 脚本全部使用 CREATE TABLE IF NOT EXISTS，可重复执行。
 */
@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
    private static final String SCRIPT = "/db/plugin-schema.sql";
    private static volatile boolean initialized = false;

    public SchemaInitializer() {
        log.info("SchemaInitializer 已实例化，等待数据源");
    }

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        ensure(dataSource);
    }

    /**
     * 幂等初始化入口：插件内任一 Spring Bean 启动时调用一次即可。
     * 兼容插件容器只扫描控制器、不扫描普通 @Component 的情况。
     */
    public static synchronized void ensure(DataSource source) {
        if (initialized) {
            return;
        }
        if (source == null) {
            log.warn("插件建表跳过：数据源为空");
            return;
        }
        long begin = System.currentTimeMillis();
        int ok = 0;
        int failed = 0;
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : loadStatements()) {
                try {
                    statement.execute(sql);
                    ok++;
                } catch (Exception e) {
                    failed++;
                    log.warn("插件建表语句执行失败：{}，错误：{}", brief(sql), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("插件初始化数据库失败", e);
            return;
        }
        initialized = true;
        log.info("插件数据库初始化完成，成功 {} 条，失败 {} 条，耗时 {} ms", ok, failed, System.currentTimeMillis() - begin);
    }

    private static List<String> loadStatements() {
        List<String> list = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(SCRIPT)) {
            if (in == null) {
                log.error("未找到建表脚本 {}", SCRIPT);
                return list;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                    continue;
                }
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            log.error("读取建表脚本失败", e);
            return list;
        }
        String all = sb.toString();
        for (String part : all.split(";")) {
            String sql = part.trim();
            if (!sql.isEmpty()) {
                list.add(sql + ";");
            }
        }
        return list;
    }

    private static String brief(String sql) {
        String one = sql.replaceAll("\\s+", " ");
        return one.length() > 90 ? one.substring(0, 90) + "..." : one;
    }
}
