package com.jsh.plugin;

import com.gitee.starblues.realize.BasePlugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 思雨食品 ERP v3.6 增强插件套件入口。
 *
 * 插件包含：生产管理、总账管理、盘点业务、多级审核、多属性库存、批次库存、
 * 序列号统计、三联打印、单据导出、利润报表、销售占比、销售排行、负库存统计、
 * 车辆管理、客户跟进、固定资产、业绩提成、销售利润。
 */
public class JshPluginSuite extends BasePlugin {

    private static final Logger log = LoggerFactory.getLogger(JshPluginSuite.class);

    public JshPluginSuite(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String scanPackage() {
        return "com.jsh.plugin";
    }

    @Override
    protected void startEvent() {
        log.info("jsh-plugin-suite 启动，插件id={}", getWrapper().getPluginId());
    }

    @Override
    protected void stopEvent() {
        log.info("jsh-plugin-suite 停止");
    }

    @Override
    protected void deleteEvent() {
        log.info("jsh-plugin-suite 卸载（数据表保留）");
    }
}
