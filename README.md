# zhu-ai-agent 后端说明

这是一个基于 Spring Boot 3 + Spring AI 的企业知识问答后端服务，提供 PDF 上传、RAG 检索问答和 SSE 流式聊天能力。阿里云体验地址：http://47.102.142.235/ （可能会欠费停服）



## 页面展示

![首页](pic/首页.png)
![对话页](pic/对话页.png)

## 主要能力

- PDF 上传：支持上传 PDF 文件并建立知识库索引
- RAG 问答：基于向量检索与引用结果回答问题
- 流式对话：支持 SSE 流式输出，前端可实时展示回答内容和引用来源
- 健康检查：提供 `/api/health` 接口用于探活

## 关键目录

- [src/main/java/com/zhubao/zhuaiagent/controller](src/main/java/com/zhubao/zhuaiagent/controller)：接口控制器
- [src/main/resources](src/main/resources)：配置文件与环境变量模板
- [docker-compose.yml](docker-compose.yml)：Docker Compose 启动配置

## 本地运行

```bash
./mvnw spring-boot:run
```

服务启动后默认监听：
- http://localhost:8123/api

常用接口示例：
- `POST /api/upload/add`：上传 PDF 文件
- `POST /api/chat/rag/stream`：流式 RAG 问答
- `GET /api/health`：健康检查

## 配置说明

后端配置主要在 [src/main/resources/application.yml](src/main/resources/application.yml) 中：
- `server.port=8123`
- `server.servlet.context-path=/api`
- `spring.ai.*`：模型与向量检索相关配置
- `spring.servlet.multipart.*`：上传文件大小限制

运行前需要准备相关环境变量，例如：
- `DASHSCOPE_API_KEY`
- `OLLAMA_BASE_URL`
- `OLLAMA_CHAT_MODEL`

## 构建与打包

```bash
./mvnw clean package
```

## Docker 部署

```bash
docker compose up --build
```

默认会启动后端容器和前端容器，端口映射如下：
- 后端：8123
- 前端：8080
