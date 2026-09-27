# cc-satellite

管理卫星过境窗口、地面站和天线资源，并在可见过境窗口内为卫星联系任务排程。

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test

## 领域模型

- **地面站（GroundStation）**：唯一编号、支持频段集合、若干天线。
- **天线（Antenna）**：属于一个地面站，定义从一颗卫星转向另一颗卫星所需的固定准备时长（`slewSeconds`，秒）。
- **过境窗口（VisibilityWindow）**：站点、卫星、开始/结束时间、可用频段。
- **联系任务（Contact）**：一次排程结果，记录幂等键、请求内容指纹、窗口、天线、频段、期望/实际起止时间与状态（`SCHEDULED` / `CANCELLED`）。

## 主要业务规则

1. **可行性**：实际联系必须完整落在过境窗口内；请求频段必须同时被窗口和地面站支持；同一天线上的任务时间不得重叠。
2. **转向间隔**：同一天线上相邻任务属于不同卫星时，间隔不得小于该天线的转向时长；属于同一卫星时可以首尾相接。插入位置同时检查前后两个相邻任务（包括窗口边界之外、但转向时长会波及窗口内的任务）。
3. **位置选择**：在所有可行位置中稳定选择——优先 |实际开始 − 期望开始| 最小，其次开始时间更早，最后天线编号更小。候选开始时间取期望时间在每个可行区间内的最近点。
4. **失败不留占用**：无法找到可行位置时返回 422，事务回滚，不留下任何天线占用。
5. **幂等**：每个请求携带幂等键。相同键 + 相同内容重放返回原排程；相同键 + 不同内容返回 409 冲突。内容指纹由窗口、频段、时长、期望开始时间组成。
6. **并发**：排程时对地面站行加悲观写锁（`PESSIMISTIC_WRITE`），同一站点上的排程串行执行，并发下不会产生时间重叠或违反转向间隔；取消时对任务行加锁，保证取消只生效一次。
7. **取消**：仅允许取消尚未开始的任务（与可替换的 `Clock` 比较当前时间），取消释放天线占用；重复取消幂等返回当前状态；任务已开始则返回 409。
8. **时间源**：服务依赖注入的 `java.time.Clock` Bean，测试中可替换为固定/可变时钟。

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/stations` | 登记地面站（编号、支持频段、天线及转向时长） |
| GET | `/api/stations/{code}/schedule` | 查询地面站日程（按天线分组的在排任务） |
| POST | `/api/windows` | 登记过境窗口 |
| POST | `/api/contacts` | 提交联系请求并排程（幂等） |
| GET | `/api/contacts/{id}` | 查询联系详情 |
| POST | `/api/contacts/{id}/cancel` | 取消未开始的联系任务 |

错误码：404 资源不存在；409 幂等键内容冲突 / 任务已开始无法取消；422 频段不兼容或无可行位置；400 请求体校验失败。

### 示例

```bash
# 登记地面站
curl -X POST localhost:8080/api/stations -H 'Content-Type: application/json' -d '{
  "code": "ST-1",
  "supportedBands": ["X", "S"],
  "antennas": [{"code": "ANT-1", "slewSeconds": 600}]
}'

# 登记过境窗口
curl -X POST localhost:8080/api/windows -H 'Content-Type: application/json' -d '{
  "stationCode": "ST-1",
  "satellite": "SAT-1",
  "startTime": "2026-06-01T10:00:00Z",
  "endTime": "2026-06-01T11:00:00Z",
  "bands": ["X"]
}'

# 排程联系任务
curl -X POST localhost:8080/api/contacts -H 'Content-Type: application/json' -d '{
  "idempotencyKey": "req-001",
  "windowId": 1,
  "band": "X",
  "durationMinutes": 30,
  "desiredStart": "2026-06-01T10:15:00Z"
}'
```
