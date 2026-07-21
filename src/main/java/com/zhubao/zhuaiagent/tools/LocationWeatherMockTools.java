package com.zhubao.zhuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class LocationWeatherMockTools {

    /**
     * 工具 a：获取当前地址（无参，Mock 固定返 "杭州"）
     */
    @Tool(name = "getLocation", description = "获取用户当前所在城市,无参数。"
            + "当需要查天气/温度等信息且不知道城市时,必须先调用此工具拿到城市名,"
            + "再传给 getWeather。禁止自行猜测城市。")
    public String getLocation() {
        // Mock：假装从 GPS / IP 定位拿到杭州
        return "杭州";
    }

    /**
     * 工具 b：获取指定城市天气（要 city 参数，来自 getLocation 的返回值）
     */
    @Tool(name = "getWeather", description = "根据城市名查询天气。"
            + "参数 city 必须是 getLocation 工具的返回值,禁止自行填写或猜测。")
    public String getWeather(
            @ToolParam(description = "城市名,必须来自 getLocation 的返回值,例如 '杭州'") String city) {
        // Mock 数据：按城市返回固定天气
        return switch (city) {
            case "杭州" -> "晴,28°C,湿度65%,东南风3级";
            case "北京" -> "多云,32°C,湿度40%,南风2级";
            case "上海" -> "小雨,26°C,湿度85%,东风4级";
            default -> "未知城市(" + city + "),暂不支持查询";
        };
    }
}