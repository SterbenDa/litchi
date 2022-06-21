package litchi.core;

import com.alibaba.fastjson.JSONObject;
import litchi.core.common.NodeInfo;
import litchi.core.common.Schedule;
import litchi.core.common.extend.ObjectReference;
import litchi.core.common.logback.LogUtils;
import litchi.core.common.utils.JsonUtils;
import litchi.core.common.utils.PathUtils;
import litchi.core.common.utils.ServerTime;
import litchi.core.components.Component;
import litchi.core.components.ComponentCallback;
import litchi.core.components.ComponentFeature;
import litchi.core.dataconfig.DataConfig;
import litchi.core.dataconfig.DataConfigComponent;
import litchi.core.dbqueue.DBQueue;
import litchi.core.dbqueue.SQLQueueComponent;
import litchi.core.dispatch.Dispatcher;
import litchi.core.dispatch.DispatcherComponent;
import litchi.core.dispatch.disruptor.ThreadInfo;
import litchi.core.event.EventComponent;
import litchi.core.jdbc.FastJdbc;
import litchi.core.net.rpc.client.RpcClientComponent;
import litchi.core.net.session.GateSessionService;
import litchi.core.net.session.NodeSessionService;
import litchi.core.redis.RedisComponent;
import litchi.core.router.RouteComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * create litchi
 *
 * @author 0x737263
 */
public class Litchi {
    private static ObjectReference<Litchi> ref = new ObjectReference<>();

    public Logger logger;

    private boolean debug;

    private String rootConfigPath;
    private String envDir;
    private String[] basePackages;
    private String envName;
    private String nodeId;

    /**
     * litchi.json json object
     */
    private JSONObject litchiConfig;

    private long startTime = ServerTime.timeMillis();

    /**
     * 所有配置的服务器信息
     * key:nodeType, value:List<NodeInfo>
     */
    private Map<String, List<NodeInfo>> nodesInfo = new ConcurrentHashMap<>();

    /**
     * 当前服务器信息
     */
    private NodeInfo currentNode;

    /**
     * components collection
     * key:class, value:Component
     */
    private Map<Class<? extends Component>, Component> componentsMap = new LinkedHashMap<>();

    private GateSessionService sessionService = new GateSessionService();
    private NodeSessionService nodeSessionService = new NodeSessionService();

    private Schedule schedule;

    public static Litchi call() {
        return ref.get();
    }

    public static Litchi createApp(String configPath, String envName, String nodeId) throws Exception {
        Litchi litchi = new Litchi(configPath, "env", envName, nodeId);
        ref.set(litchi);
        litchi.init();
        return ref.get();
    }

    public static Litchi createApp(String configPath, String envDir, String envName, String nodeId) throws Exception {
        Litchi litchi = new Litchi(configPath, envDir, envName, nodeId);
        ref.set(litchi);
        litchi.init();
        return ref.get();
    }

    /**
     * @param configPath 配置文件根路径
     * @param envDir     基于configPath根路径的环境目录
     * @param nodeId     服务器结点id
     */
    private Litchi(String configPath, String envDir, String envName, String nodeId) throws Exception {
        this.rootConfigPath = configPath;
        this.envDir = envDir;
        this.envName = envName;
        this.nodeId = nodeId;

        if (this.rootConfigPath == null || this.envDir == null || this.envName == null || this.nodeId == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("litchi VM options config error...\n");
            sb.append("-Dlitchi.config      \t 配置文件相对根路径.  eg. -Dlitchi.config=config \n");
            sb.append("-Dlitchi.env         \t运行环境名称.  eg. -Dlitchi.env=local \n");
            sb.append("-Dlitchi.nodeid      \t当前服务器的结点id.  eg. -Dlitchi.nodeid=gate-1 \n");
            throw new Exception(sb.toString());
        }

        //check env path
        File path = new File(getEnvPath());
        if (!path.isDirectory()) {
            throw new Exception(String.format("file:%s  is not directory.", getEnvPath()));
        }
    }

    private void init() throws Exception {
        //init logback.xml configure
        String logbackPath = Paths.get(getEnvPath(), Constants.File.LOG_BACK).toString();
        LogUtils.loadFileConfig(logbackPath);

        this.logger = LoggerFactory.getLogger(Litchi.class);

        logger.info("========== node launcher ==========");
        logger.info("nodeId:{}, configPath:{}, envName:{}", this.nodeId, this.rootConfigPath, this.envName);

        loadLitchiConfig();
        loadNodesConfig();

        // add default component
        addComponent(new EventComponent(this));
        addComponent(new RouteComponent(this));
    }

    /**
     * read litchi.json
     */
    private void loadLitchiConfig() {
        this.litchiConfig = JsonUtils.read(getEnvPath(), Constants.File.LITCHI_DOT_JSON);
        //start BasePackage
        this.basePackages = JsonUtils.readStringArray(this.litchiConfig, Constants.Component.BASE_PACKAGES);
        this.debug = this.litchiConfig.getBoolean("debug");
    }

    /**
     * read nodes.json
     */
    private void loadNodesConfig() throws Exception {
        JSONObject jsonObject = JsonUtils.read(getEnvPath(), Constants.File.NODES_DOT_JSON);
        this.nodesInfo.putAll(NodeInfo.getNodeMaps(jsonObject, this.nodeId));

        for (List<NodeInfo> list : nodesInfo.values()) {
            for (NodeInfo si : list) {
                if (si.getNodeId().equals(nodeId)) {
                    this.currentNode = si;
                }
            }
        }

        if (this.currentNode == null) {
            List<String> idList = new ArrayList<>();
            for (List<NodeInfo> list : nodesInfo.values()) {
                for (NodeInfo serverInfo : list) {
                    idList.add(serverInfo.getNodeId());
                }
            }

            throw new Exception(String.format("can not found current node. nodeId=%s, %s", nodeId, idList));
        }
    }

    public Litchi addComponent(Component component) {
        this.componentsMap.put(component.getClass(), component);
        return this;
    }

    public Litchi addComponent(ComponentFeature<Component> feature) {
        Component c = feature.createComponent(this);
        this.componentsMap.put(c.getClass(), c);
        return this;
    }

    public Litchi setDataConfig() {
        this.setDataConfig(new DataConfigComponent(this));
        return this;
    }

    public Litchi setDataConfig(DataConfig dataConfig) {
        this.addComponent(dataConfig);
        return this;
    }

    public Litchi setDBQueue() {
        this.setDBQueue(new SQLQueueComponent(this));
        return this;
    }

    public Litchi setDBQueue(DBQueue dbQueue) {
        this.addComponent(dbQueue);
        return this;
    }

    public Litchi setDispatch(List<ThreadInfo> threadList) {
        this.setDispatch(new DispatcherComponent(this, threadList));
        return this;
    }

    public Litchi setDispatch(Dispatcher dispatch) {
        this.addComponent(dispatch);
        return this;
    }

    public Litchi setJdbc() {
        this.addComponent(new FastJdbc(this));
        return this;
    }

    /**
     * @param nodeTypes connect to nodeTypes
     * @return
     */
    public Litchi setConnectRpc(String... nodeTypes) {
        RpcClientComponent rpcClient = getComponent(RpcClientComponent.class);
        if (rpcClient == null) {
            rpcClient = new RpcClientComponent(this);
            rpcClient.add(nodeTypes);
            this.addComponent(rpcClient);
        }
        return this;
    }

    /**
     * redis集群连接池
     *
     * @return
     */
    public Litchi setRedis() {
        this.addComponent(new RedisComponent(this));
        return this;
    }

    public long getStartTime() {
        return startTime;
    }

    private Component judgeComponent(Object item) {
        if (item instanceof ComponentFeature) {
            return (Component) ((ComponentFeature) item).createComponent(this);
        }
        return (Component) item;
    }

    /**
     * 开始运行
     *
     * @param callback
     */
    public void start(ComponentCallback... callback) {
        // order by execute component->start()
        for (Component c : this.componentsMap.values()) {
            logger.debug("[component->start()] name = {}", c.name());
            c.start();
        }

        // execute component->afterStart()

        for (Component c : this.componentsMap.values()) {
            logger.debug("[component->afterStart()] name = {}", c.name());
            c.afterStart();
        }

        // execute callback
        for (ComponentCallback c : callback) {
            c.execute(this);
        }

        // shutdown hook
        shutdownHook();

        logger.info(currentNode().toString());
        logger.warn("========== {} launcher complete runTime:{}ms ==========", this.getNodeId(), ServerTime.timeMillis() - this.startTime);
    }

    private void shutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            long stopTime = ServerTime.timeMillis();

            if (this.schedule != null) {
                this.schedule.shutdown();
            }


            //reverse process component list
            List<Component> components = new ArrayList<>(this.componentsMap.values());
            Collections.reverse(components);

            //beforeStop()
            for (Component c : components) {
                try {
                    logger.debug("[component->beforeStop()] name = {}", c.name());
                    c.beforeStop();
                } catch (Exception e) {
                    logger.error("{}", e);
                }
            }

            //stop()
            for (Component c : components) {
                try {
                    logger.debug("[component->stop()] name = {}", c.name());
                    c.stop();
                } catch (Exception e) {
                    logger.error("{}", e);
                }
            }

            //倒着停服务
            this.componentsMap.forEach((name, component) -> {

            });

            logger.info("current node info: {}", this.currentNode);
            logger.info("========== {} server has stopped. time:{}ms ==========", this.getNodeId(), (ServerTime.timeMillis() - stopTime));
        }));
    }

    /**
     * 是否为debug模式
     *
     * @return
     */
    public boolean isDebug() {
        return this.debug;
    }

    /**
     * get component by class info
     *
     * @param clazz
     * @param <T>
     * @return
     */
    public <T extends Component> T getComponent(Class<? extends Component> clazz) {
        Component c = this.componentsMap.get(clazz);
        if (c == null) {
            final Optional<Component> result = this.componentsMap.values()
                    .stream()
                    .filter((value) -> clazz.isInstance(value))
                    .findFirst();
            if (result.isPresent()) {
                return (T) result.get();
            }
        }
        return (T) c;
    }

    public String[] packagesName() {
        return this.basePackages;
    }

    public String getRootConfigPath() {
        return this.rootConfigPath;
    }

    public String getEnvPath() {
        return PathUtils.combine(this.rootConfigPath, this.envDir, this.envName);
    }

    public String getEnvName() {
        return this.envName;
    }

    public RedisComponent redis() {
        return getComponent(RedisComponent.class);
    }

    public DataConfig data() {
        return getComponent(DataConfig.class);
    }

    public DBQueue dbQueue() {
        return getComponent(DBQueue.class);
    }

    public Dispatcher dispatch() {
        return getComponent(Dispatcher.class);
    }

    public FastJdbc jdbc() {
        return getComponent(FastJdbc.class);
    }

    public RpcClientComponent rpc() {
        return getComponent(RpcClientComponent.class);
    }

    public JSONObject config() {
        return this.litchiConfig;
    }

    public JSONObject config(String nodeName) {
        return this.litchiConfig.getJSONObject(nodeName);
    }

    public String getNodeId() {
        return this.nodeId;
    }

    public NodeInfo currentNode() {
        return this.currentNode;
    }

    public NodeInfo getNodeInfo(String serverType, String serverId) {
        Collection<NodeInfo> serverInfoCollection = getNodeInfoList(serverType);
        for (NodeInfo serverInfo : serverInfoCollection) {
            if (serverInfo.getNodeId().equals(serverId)) {
                return serverInfo;
            }
        }
        return null;
    }

    public List<NodeInfo> getNodeInfoList(String serverType) {
        return this.nodesInfo.getOrDefault(serverType, new ArrayList<>());
    }

    public RouteComponent route() {
        return getComponent(RouteComponent.class);
    }

    public EventComponent event() {
        return getComponent(EventComponent.class);
    }

    public GateSessionService sessionService() {
        return this.sessionService;
    }

    public NodeSessionService nodeSessionService() {
        return nodeSessionService;
    }

    public Litchi setSchedule(int threadSize) {
        if (this.schedule == null) {
            this.schedule = new Schedule(threadSize, "litchi-schedule");
        }
        return this;
    }

    public Schedule schedule() {
        if (this.schedule == null) {
            setSchedule(4);
        }
        return this.schedule;
    }
}
