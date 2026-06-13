import React, {useEffect, useState} from "react";
import {Button, Descriptions, Drawer, Grid, Popconfirm, Space, Table, Tag, Tooltip, Typography, message} from "antd";
import {InfoCircleOutlined, PoweroffOutlined} from "@ant-design/icons";
import type {ColumnsType} from "antd/es/table";
import axios from "axios";
import {apiPath, Capability, formatEpoch, formatTime, InvocationLog, paginationFromResponse, rowsFromResponse, RuntimeInstanceState, RuntimePagination, useCapabilityView} from "./common";

const {Text} = Typography;

type RuntimeStatesTabProps = {
    dark: boolean;
}

type RuntimeVisibleStatus = "stopped" | "starting" | "running" | "executing" | "failed";

const runtimeVisibleStatus = (record: RuntimeInstanceState): RuntimeVisibleStatus => {
    if (record.status === "failed") {
        return "failed";
    }
    if (Number(record.activeInvocationCount || 0) > 0 || record.effectiveStatus === "executing") {
        return "executing";
    }
    if (record.status === "starting" || record.status === "initializing") {
        return "starting";
    }
    if (record.status === "ready" || record.status === "idle") {
        return "running";
    }
    return "stopped";
};

const runtimeStatusLabel = (value: RuntimeVisibleStatus) => {
    const labels: Record<RuntimeVisibleStatus, string> = {
        stopped: "未运行",
        starting: "启动中",
        running: "运行中",
        executing: "执行中",
        failed: "异常"
    };
    return labels[value];
};

const runtimeStatusColor = (value: RuntimeVisibleStatus) => {
    const colors: Record<RuntimeVisibleStatus, string> = {
        stopped: "default",
        starting: "processing",
        running: "success",
        executing: "processing",
        failed: "error"
    };
    return colors[value];
};

const formatBytes = (value?: number) => {
    if (value == null) {
        return "-";
    }
    const units = ["B", "KB", "MB", "GB", "TB"];
    let size = value;
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
        size = size / 1024;
        unitIndex += 1;
    }
    const fixed = size >= 10 || unitIndex === 0 ? 0 : 1;
    return `${size.toFixed(fixed)} ${units[unitIndex]}`;
};

const formatDuration = (value?: number) => {
    if (value == null) {
        return "-";
    }
    if (value < 1000) {
        return `${value} ms`;
    }
    const seconds = Math.floor(value / 1000);
    if (seconds < 60) {
        return `${seconds} s`;
    }
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes < 60) {
        return `${minutes} m ${remainingSeconds} s`;
    }
    const hours = Math.floor(minutes / 60);
    return `${hours} h ${minutes % 60} m`;
};

const processAliveTag = (value?: boolean, key?: string) => {
    if (value == null) {
        return <Tag key={key}>未知</Tag>;
    }
    return value ? <Tag key={key} color="success">可用</Tag> : <Tag key={key} color="error">异常</Tag>;
};

const pluginVersionLabel = (value?: string) => {
    const version = value?.trim();
    return version ? `v${version}` : undefined;
};

const RuntimeStatesTab: React.FC<RuntimeStatesTabProps> = () => {
    const screens = Grid.useBreakpoint();
    const isMobile = Boolean((screens.xs || screens.sm) && !screens.md);
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});
    const [loading, setLoading] = useState(false);
    const [stateActions, setStateActions] = useState<Record<string, boolean>>({});
    const [selectedState, setSelectedState] = useState<RuntimeInstanceState | null>(null);
    const [capabilities, setCapabilities] = useState<Capability[]>([]);
    const [states, setStates] = useState<RuntimeInstanceState[]>([]);
    const [invocationLogs, setInvocationLogs] = useState<InvocationLog[]>([]);
    const [invocationLogPagination, setInvocationLogPagination] = useState<RuntimePagination>({
        current: 1,
        pageSize: 10,
        total: 0
    });
    const {renderPlugin, renderCapability} = useCapabilityView(capabilities);

    const loadData = async (logPage = invocationLogPagination.current, logPageSize = invocationLogPagination.pageSize) => {
        setLoading(true);
        try {
            const [capabilitiesRes, statesRes, logsRes] = await Promise.all([
                axios.get(apiPath("/runtime-capabilities")),
                axios.get(apiPath("/runtime-states")),
                axios.get(apiPath("/runtime-invocation-logs"), {params: {page: logPage, pageSize: logPageSize}})
            ]);
            setCapabilities(capabilitiesRes.data.items || []);
            setStates(statesRes.data.items || []);
            setInvocationLogs(rowsFromResponse<InvocationLog>(logsRes.data));
            setInvocationLogPagination(paginationFromResponse<InvocationLog>(logsRes.data, {
                current: logPage,
                pageSize: logPageSize,
                total: 0
            }));
        } catch (e) {
            messageApi.error("运行态数据加载失败");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const stateActionKey = (record: RuntimeInstanceState, action: string) => `${record.pluginId}:${record.instanceId}:${action}`;

    const setStateActionLoading = (record: RuntimeInstanceState, action: string, loading: boolean) => {
        setStateActions(prev => ({...prev, [stateActionKey(record, action)]: loading}));
    };

    const stopPlugin = async (record: RuntimeInstanceState) => {
        setStateActionLoading(record, "stop", true);
        try {
            const params = new URLSearchParams();
            params.set("pluginId", record.pluginId);
            const {data} = await axios.post(apiPath("/runtime-states/stop"), params.toString());
            if (data.code > 0) {
                messageApi.error(data.message);
                return;
            }
            messageApi.success("插件已停止");
            await loadData(invocationLogPagination.current, invocationLogPagination.pageSize);
        } finally {
            setStateActionLoading(record, "stop", false);
        }
    };

    const stopDisabled = (record: RuntimeInstanceState) =>
        !record.local || !["ready", "idle"].includes(record.status) || Number(record.activeInvocationCount || 0) > 0;
    const runtimeStatusTag = (record: RuntimeInstanceState) => {
        const status = runtimeVisibleStatus(record);
        return <Tag color={runtimeStatusColor(status)}>{runtimeStatusLabel(status)}</Tag>;
    };
    const resourceSummary = (record: RuntimeInstanceState) => {
        const tags: React.ReactNode[] = [];
        if (record.processAlive != null) {
            tags.push(processAliveTag(record.processAlive, "alive"));
        }
        if (record.residentMemoryBytes != null) {
            tags.push(<Tag key="rss">RSS {formatBytes(record.residentMemoryBytes)}</Tag>);
        }
        if (record.heapUsedBytes != null) {
            tags.push(<Tag key="heap">堆 {formatBytes(record.heapUsedBytes)}</Tag>);
        }
        if (record.threadCount != null) {
            tags.push(<Tag key="threads">线程 {record.threadCount}</Tag>);
        }
        if (tags.length === 0) {
            return <Text type="secondary">-</Text>;
        }
        return <Space size={[4, 4]} wrap>{tags}</Space>;
    };
    const runtimePluginCell = (record: RuntimeInstanceState) => (
        <Space direction="vertical" size={8} style={{width: "100%", minWidth: 0}}>
            {renderPlugin(record.pluginId, record.pluginName, record.pluginPreviewImageBase64, undefined, pluginVersionLabel(record.pluginVersion))}
            {isMobile && (
                <Space direction="vertical" size={4} style={{width: "100%"}}>
                    <Space size={[4, 4]} wrap>
                        {runtimeStatusTag(record)}
                        <Tag style={{margin: 0}}>调用 {record.activeInvocationCount || 0}</Tag>
                        {resourceSummary(record)}
                    </Space>
                    <Text type="secondary" style={{fontSize: 12}}>活动 {formatEpoch(record.lastActiveAt)}</Text>
                </Space>
            )}
        </Space>
    );
    const runtimeStatusCell = (record: RuntimeInstanceState) => (
        <Space direction="vertical" size={4}>
            {runtimeStatusTag(record)}
            <Tag style={{margin: 0}}>调用 {record.activeInvocationCount || 0}</Tag>
        </Space>
    );

    const stateColumns: ColumnsType<RuntimeInstanceState> = [
        {
            title: "插件",
            key: "plugin",
            width: isMobile ? undefined : 260,
            render: (_, record) => (
                runtimePluginCell(record)
            )
        },
        {
            title: "运行状态",
            key: "status",
            width: 130,
            responsive: ["md"],
            render: (_, record) => runtimeStatusCell(record)
        },
        {
            title: "资源",
            key: "resource",
            width: 260,
            responsive: ["md"],
            render: (_, record) => resourceSummary(record)
        },
        {title: "最后活动", dataIndex: "lastActiveAt", width: 180, render: formatEpoch, responsive: ["md"]},
        {
            title: "操作",
            key: "action",
            width: isMobile ? 96 : 160,
            render: (_, record) => (
                <Space size={isMobile ? 2 : "small"}>
                    <Tooltip title="详情">
                        <Button
                            type={isMobile ? "text" : "link"}
                            size="small"
                            icon={<InfoCircleOutlined />}
                            aria-label="详情"
                            onClick={() => setSelectedState(record)}
                        >
                            {!isMobile && "详情"}
                        </Button>
                    </Tooltip>
                    <Popconfirm title="停止这个本机插件进程？" okText="停止" cancelText="取消" onConfirm={() => stopPlugin(record)}>
                        <Button
                            danger
                            type={isMobile ? "text" : "link"}
                            size="small"
                            icon={<PoweroffOutlined />}
                            aria-label="停止"
                            disabled={stopDisabled(record)}
                            loading={stateActions[stateActionKey(record, "stop")]}
                        >
                            {!isMobile && "停止"}
                        </Button>
                    </Popconfirm>
                </Space>
            )
        }
    ];
    const detailDrawer = (
        <Drawer
            title="运行实例"
            open={Boolean(selectedState)}
            onClose={() => setSelectedState(null)}
            width={isMobile ? "100%" : 640}
        >
            {selectedState && (
                <Space direction="vertical" size={16} style={{width: "100%"}}>
                    {renderPlugin(selectedState.pluginId, selectedState.pluginName, selectedState.pluginPreviewImageBase64, undefined, pluginVersionLabel(selectedState.pluginVersion))}
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label="插件版本">{pluginVersionLabel(selectedState.pluginVersion) || "-"}</Descriptions.Item>
                        <Descriptions.Item label="状态">
                            <Space size={[4, 4]} wrap>
                                {runtimeStatusTag(selectedState)}
                                {selectedState.local ? <Tag color="success">本机</Tag> : <Tag>远端/未知</Tag>}
                                <Tag style={{margin: 0}}>调用 {selectedState.activeInvocationCount || 0}</Tag>
                            </Space>
                        </Descriptions.Item>
                        <Descriptions.Item label="实例">
                            <Text copyable ellipsis style={{maxWidth: "100%"}}>{selectedState.instanceId}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="进程号">{selectedState.processId || "-"}</Descriptions.Item>
                        <Descriptions.Item label="运行模式">{selectedState.runtimeMode || "-"}</Descriptions.Item>
                        <Descriptions.Item label="连接时间">{formatEpoch(selectedState.readyAt)}</Descriptions.Item>
                        <Descriptions.Item label="最后活动">{formatEpoch(selectedState.lastActiveAt)}</Descriptions.Item>
                        <Descriptions.Item label="最后心跳">{formatEpoch(selectedState.heartbeatAt)}</Descriptions.Item>
                        <Descriptions.Item label="租约过期">{formatEpoch(selectedState.leaseExpiresAt)}</Descriptions.Item>
                    </Descriptions>
                    <Descriptions bordered size="small" column={1} title="资源">
                        <Descriptions.Item label="进程响应">{processAliveTag(selectedState.processAlive)}</Descriptions.Item>
                        <Descriptions.Item label="采样时间">{formatEpoch(selectedState.processSampledAt)}</Descriptions.Item>
                        <Descriptions.Item label="RSS">{formatBytes(selectedState.residentMemoryBytes)}</Descriptions.Item>
                        <Descriptions.Item label="虚拟内存">{formatBytes(selectedState.virtualMemoryBytes)}</Descriptions.Item>
                        <Descriptions.Item label="堆已用">{formatBytes(selectedState.heapUsedBytes)}</Descriptions.Item>
                        <Descriptions.Item label="堆已提交">{formatBytes(selectedState.heapCommittedBytes)}</Descriptions.Item>
                        <Descriptions.Item label="堆上限">{formatBytes(selectedState.heapMaxBytes)}</Descriptions.Item>
                        <Descriptions.Item label="CPU 时间">{formatDuration(selectedState.totalCpuDurationMillis)}</Descriptions.Item>
                        <Descriptions.Item label="线程数">{selectedState.threadCount == null ? "-" : selectedState.threadCount}</Descriptions.Item>
                    </Descriptions>
                    {(selectedState.lastError || selectedState.processErrorMessage) && (
                        <Descriptions bordered size="small" column={1} title="错误">
                            {selectedState.lastError && (
                                <Descriptions.Item label="运行错误">
                                    <Text type="danger">{selectedState.lastError}</Text>
                                </Descriptions.Item>
                            )}
                            {selectedState.processErrorMessage && (
                                <Descriptions.Item label="进程查询">
                                    <Text type="danger">{selectedState.processErrorMessage}</Text>
                                </Descriptions.Item>
                            )}
                        </Descriptions>
                    )}
                </Space>
            )}
        </Drawer>
    );

    const invocationStatusTag = (value: string) => value === "success" ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>;
    const invocationRiskTag = (value?: string) => {
        if (!value) {
            return null;
        }
        const labels: Record<string, string> = {
            low: "低风险",
            medium: "中风险",
            high: "高风险",
            critical: "关键风险"
        };
        const colors: Record<string, string> = {
            low: "default",
            medium: "warning",
            high: "orange",
            critical: "error"
        };
        return <Tag color={colors[value] || "default"}>{labels[value] || value}</Tag>;
    };
    const invocationSourceLabel = (value?: string) => {
        const labels: Record<string, string> = {
            scheduler: "定时调度",
            tick: "手动/外部",
            notification: "通知",
            runtime_event: "运行时事件",
            internal: "内部调用",
            admin_ui: "后台页面",
            mcp: "MCP"
        };
        return value ? (labels[value] || value) : "-";
    };
    const invocationLogCell = (record: InvocationLog) => (
        <Space direction="vertical" size={8} style={{width: "100%", minWidth: 0}}>
            {renderCapability(record.pluginId, record.capabilityKey, undefined, record.pluginPreviewImageBase64, record.pluginName)}
            {isMobile && (
                <Space direction="vertical" size={4} style={{width: "100%"}}>
                    <Space size={[4, 4]} wrap>
                        {invocationStatusTag(record.status)}
                        {record.source && <Tag>{invocationSourceLabel(record.source)}</Tag>}
                        {invocationRiskTag(record.riskLevel)}
                        {record.auditRequired && <Tag color="processing">审计</Tag>}
                        {record.durationMs != null && <Tag>{record.durationMs} ms</Tag>}
                    </Space>
                    <Text type="secondary" style={{fontSize: 12}}>{formatEpoch(record.startedAt)}</Text>
                    {record.errorMessage && (
                        <Text type="danger" ellipsis style={{fontSize: 12, maxWidth: "100%"}}>
                            {record.errorMessage}
                        </Text>
                    )}
                </Space>
            )}
        </Space>
    );

    const invocationLogColumns: ColumnsType<InvocationLog> = [
        {title: "能力", key: "capability", render: (_, record) => invocationLogCell(record)},
        {title: "来源", dataIndex: "source", width: 120, render: invocationSourceLabel, responsive: ["md"]},
        {
            title: "状态",
            dataIndex: "status",
            width: 100,
            responsive: ["md"],
            render: invocationStatusTag
        },
        {
            title: "风险",
            dataIndex: "riskLevel",
            width: 140,
            responsive: ["md"],
            render: (value?: string, record?: InvocationLog) => (
                <Space size={[4, 4]} wrap>
                    {invocationRiskTag(value)}
                    {record?.auditRequired && <Tag color="processing">审计</Tag>}
                </Space>
            )
        },
        {title: "耗时", dataIndex: "durationMs", width: 100, render: (value?: number) => value == null ? "-" : `${value} ms`, responsive: ["md"]},
        {title: "开始时间", dataIndex: "startedAt", width: 240, render: formatEpoch, responsive: ["md"]},
        {title: "错误", dataIndex: "errorMessage", render: formatTime, responsive: ["md"]}
    ];

    return (
        <Space direction="vertical" size={16} style={{width: "100%"}}>
            {contextHolder}
            <Table<RuntimeInstanceState>
                loading={loading}
                rowKey={record => `${record.pluginId}:${record.instanceId}`}
                columns={stateColumns}
                dataSource={states}
                pagination={false}
                scroll={isMobile ? undefined : {x: 1000}}
            />
            {detailDrawer}
            <Text strong>能力调用日志</Text>
            <Table<InvocationLog>
                loading={loading}
                rowKey="id"
                columns={invocationLogColumns}
                dataSource={invocationLogs}
                pagination={{...invocationLogPagination, showSizeChanger: !isMobile}}
                onChange={pagination => loadData(pagination.current || 1, pagination.pageSize || 10)}
                scroll={isMobile ? undefined : {x: 1100}}
            />
        </Space>
    );
};

export default RuntimeStatesTab;
