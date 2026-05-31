import React, {useEffect, useState} from "react";
import {Button, Grid, Popconfirm, Space, Table, Tag, Typography, message} from "antd";
import {PoweroffOutlined} from "@ant-design/icons";
import type {ColumnsType} from "antd/es/table";
import axios from "axios";
import {apiPath, Capability, formatEpoch, formatTime, InvocationLog, paginationFromResponse, RuntimeInstanceState, RuntimePagination, useCapabilityView} from "./common";

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

const RuntimeStatesTab: React.FC<RuntimeStatesTabProps> = () => {
    const screens = Grid.useBreakpoint();
    const isMobile = Boolean((screens.xs || screens.sm) && !screens.md);
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});
    const [loading, setLoading] = useState(false);
    const [stateActions, setStateActions] = useState<Record<string, boolean>>({});
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
            setInvocationLogs(logsRes.data.items || []);
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
    const runtimePluginCell = (record: RuntimeInstanceState) => (
        <Space direction="vertical" size={8} style={{width: "100%", minWidth: 0}}>
            {renderPlugin(record.pluginId, record.pluginName, record.pluginPreviewImageBase64)}
            {isMobile && (
                <Space direction="vertical" size={4} style={{width: "100%"}}>
                    <Space size={[4, 4]} wrap>
                        {runtimeStatusTag(record)}
                        {record.local ? <Tag color="success">本机</Tag> : <Tag>远端/未知</Tag>}
                        <Tag style={{margin: 0}}>调用 {record.activeInvocationCount || 0}</Tag>
                    </Space>
                    <Text copyable ellipsis type="secondary" style={{fontSize: 12, maxWidth: "100%"}}>
                        实例 {record.instanceId}
                    </Text>
                    <Text type="secondary" style={{fontSize: 12}}>活动 {formatEpoch(record.lastActiveAt)}</Text>
                    {record.lastError && (
                        <Text type="danger" ellipsis style={{fontSize: 12, maxWidth: "100%"}}>
                            {record.lastError}
                        </Text>
                    )}
                </Space>
            )}
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
            title: "实例",
            dataIndex: "instanceId",
            width: 300,
            responsive: ["md"],
            render: (value: string) => <Text copyable ellipsis style={{maxWidth: 280}}>{value}</Text>
        },
        {title: "进程号", dataIndex: "processId", width: 100, render: (value?: number) => value || "-", responsive: ["lg"]},
        {
            title: "归属",
            dataIndex: "local",
            width: 100,
            responsive: ["md"],
            render: (value?: boolean) => value ? <Tag color="success">本机</Tag> : <Tag>远端/未知</Tag>
        },
        {
            title: "运行状态",
            key: "status",
            width: 110,
            responsive: ["md"],
            render: (_, record) => runtimeStatusTag(record)
        },
        {title: "活动调用", dataIndex: "activeInvocationCount", width: 100, responsive: ["md"]},
        {title: "连接时间", dataIndex: "readyAt", width: 180, render: formatEpoch, responsive: ["lg"]},
        {title: "最后活动", dataIndex: "lastActiveAt", width: 180, render: formatEpoch, responsive: ["md"]},
        {title: "最后心跳", dataIndex: "heartbeatAt", width: 180, render: formatEpoch, responsive: ["lg"]},
        {title: "最后错误", dataIndex: "lastError", render: formatTime, responsive: ["md"]},
        {
            title: "操作",
            key: "action",
            width: isMobile ? 64 : 120,
            render: (_, record) => (
                <Space>
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

    const invocationStatusTag = (value: string) => value === "success" ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>;
    const invocationLogCell = (record: InvocationLog) => (
        <Space direction="vertical" size={8} style={{width: "100%", minWidth: 0}}>
            {renderCapability(record.pluginId, record.capabilityKey, undefined, record.pluginPreviewImageBase64, record.pluginName)}
            {isMobile && (
                <Space direction="vertical" size={4} style={{width: "100%"}}>
                    <Space size={[4, 4]} wrap>
                        {invocationStatusTag(record.status)}
                        {record.source && <Tag>{record.source}</Tag>}
                        {record.durationMs != null && <Tag>{record.durationMs} ms</Tag>}
                    </Space>
                    <Text type="secondary" style={{fontSize: 12}}>{formatTime(record.startedAt)}</Text>
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
        {title: "来源", dataIndex: "source", width: 120, render: formatTime, responsive: ["md"]},
        {
            title: "状态",
            dataIndex: "status",
            width: 100,
            responsive: ["md"],
            render: invocationStatusTag
        },
        {title: "耗时", dataIndex: "durationMs", width: 100, render: (value?: number) => value == null ? "-" : `${value} ms`, responsive: ["md"]},
        {title: "开始时间", dataIndex: "startedAt", width: 240, render: formatTime, responsive: ["md"]},
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
                scroll={isMobile ? undefined : {x: 1300}}
            />
            <Text strong>调用日志</Text>
            <Table<InvocationLog>
                loading={loading}
                rowKey="id"
                columns={invocationLogColumns}
                dataSource={invocationLogs}
                pagination={{...invocationLogPagination, showSizeChanger: !isMobile}}
                onChange={pagination => loadData(pagination.current || 1, pagination.pageSize || 10)}
                scroll={isMobile ? undefined : {x: 960}}
            />
        </Space>
    );
};

export default RuntimeStatesTab;
