package com.zhubao.zhuaiagent.demo.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

public class GraphPrintDemo {

    public static void main(String[] args) throws Exception {

        // 1. 状态策略
        KeyStrategyFactory ksf = () -> {
            Map<String, com.alibaba.cloud.ai.graph.KeyStrategy> m = new HashMap<>();
            m.put("data", new ReplaceStrategy());
            m.put("valid", new ReplaceStrategy());
            return m;
        };

        // 2. 三个普通节点
        NodeAction inputNode  = state -> Map.of("data", "raw");
        NodeAction validateNode = state -> Map.of("valid", true);
        NodeAction processNode = state -> Map.of("data", "processed");
        NodeAction errorNode   = state -> Map.of("data", "err");

        // 3. 建图：input → validate → [valid?] → process/error → END
        StateGraph graph = new StateGraph(ksf)
                .addNode("input", AsyncNodeAction.node_async(inputNode))
                .addNode("validate", AsyncNodeAction.node_async(validateNode))
                .addNode("process", AsyncNodeAction.node_async(processNode))
                .addNode("error", AsyncNodeAction.node_async(errorNode))

                .addEdge(START, "input")
                .addEdge("input", "validate")
                .addConditionalEdges("validate",
                        AsyncEdgeAction.edge_async(state -> Boolean.TRUE.equals(state.value("valid").orElse(false))
                                ? "process" : "error"),  Map.of(
                                "process", "process",
                                "error",   "error"
                        ))
                .addEdge("process", END)
                .addEdge("error", END);

        // 4. 编译（可选，编译前后都能打）
        var compiled = graph.compile();

        // 5. 打印 —— 这是重点 👇

        // PlantUML 版
        GraphRepresentation plantuml = compiled.getGraph(
                GraphRepresentation.Type.PLANTUML, "条件分支工作流");
        System.out.println("=== PlantUML ===");
        System.out.println(plantuml.content());

        // Mermaid 版
        GraphRepresentation mermaid = compiled.getGraph(
                GraphRepresentation.Type.MERMAID, "条件分支工作流");
        System.out.println("=== Mermaid ===");
        System.out.println(mermaid.content());

        System.out.println("把这段贴到 plantuml.com/plantuml或 IDEA 的 PlantUML 插件里就能出图。\n" +
                "Mermaid\u200B 同理，贴 mermaid.live看。");
    }
}