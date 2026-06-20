import React, {useEffect, useState} from "react";
import {Button, Grid, Space, Table, Tag, Tooltip, Typography, message} from "antd";
import {CheckOutlined, ReloadOutlined} from "@ant-design/icons";
import type {ColumnsType} from "antd/es/table";
import axios from "axios";
import {
    apiPath,
    Capability,
    CommentProviderRow,
    renderPluginIdentity,
    ServiceProviderRow,
    textOrEmpty,
    useCapabilityView
} from "./common";

const {Text} = Typography;

const ServiceRuntimeTab: React.FC = () => {
    const screens = Grid.useBreakpoint();
    const isMobile = Boolean((screens.xs || screens.sm) && !screens.md);
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});
    const [loading, setLoading] = useState(false);
    const [capabilities, setCapabilities] = useState<Capability[]>([]);
    const [providers, setProviders] = useState<ServiceProviderRow[]>([]);
    const [commentProviders, setCommentProviders] = useState<CommentProviderRow[]>([]);
    const {renderCapability} = useCapabilityView(capabilities);

    const loadData = async () => {
        setLoading(true);
        try {
            const [capabilitiesRes, providersRes, commentProvidersRes] = await Promise.all([
                axios.get(apiPath("/runtime-capabilities")),
                axios.get(apiPath("/runtime-services/providers")),
                axios.get(apiPath("/runtime-services/comment-providers"))
            ]);
            setCapabilities(capabilitiesRes.data.items || []);
            setProviders(providersRes.data.items || []);
            setCommentProviders(commentProvidersRes.data.items || []);
        } catch (e) {
            messageApi.error("服务配置加载失败");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const setDefaultProvider = async (row: ServiceProviderRow) => {
        const params = new URLSearchParams();
        params.set("serviceName", row.serviceName);
        params.set("pluginId", row.providerPluginId);
        params.set("capabilityKey", row.capabilityKey);
        const {data: resp} = await axios.post(apiPath("/runtime-services/provider"), params.toString());
        if (resp.code > 0) {
            messageApi.error(resp.message);
            return;
        }
        messageApi.success("已保存");
        await loadData();
    };

    const restoreAutoProvider = async (serviceName: string) => {
        const params = new URLSearchParams();
        params.set("serviceName", serviceName);
        const {data: resp} = await axios.post(apiPath("/runtime-services/provider/auto"), params.toString());
        if (resp.code > 0) {
            messageApi.error(resp.message);
            return;
        }
        messageApi.success("已恢复自动选择");
        await loadData();
    };

    const setCommentProvider = async (row: CommentProviderRow) => {
        const params = new URLSearchParams();
        params.set("shortName", row.shortName);
        const {data: resp} = await axios.post(apiPath("/runtime-services/comment-provider"), params.toString());
        if (resp.code > 0) {
            messageApi.error(resp.message);
            return;
        }
        messageApi.success("已保存");
        await loadData();
    };

    const restoreDefaultCommentProvider = async () => {
        const {data: resp} = await axios.post(apiPath("/runtime-services/comment-provider/default"));
        if (resp.code > 0) {
            messageApi.error(resp.message);
            return;
        }
        messageApi.success("已恢复默认评论插件");
        await loadData();
    };

    const statusTags = (selected: boolean, reviewRequired: boolean, confirmed: boolean) => (
        <Space size={[4, 4]} wrap>
            {selected && <Tag color="success">当前</Tag>}
            {reviewRequired && <Tag color="warning">需确认</Tag>}
            {confirmed && <Tag color="blue">已确认</Tag>}
        </Space>
    );
    const serviceNameCell = (record: ServiceProviderRow) => (
        <Space direction="vertical" size={0}>
            <Text strong>{textOrEmpty(record.serviceLabel) || record.serviceName}</Text>
            <Tooltip title={record.serviceName}>
                <Text type="secondary" style={{fontSize: 12}}>系统服务</Text>
            </Tooltip>
        </Space>
    );
    const serviceProviderCell = (record: ServiceProviderRow) => (
        <Space direction="vertical" size={8} style={{width: "100%", minWidth: 0}}>
            {renderCapability(record.providerPluginId, record.capabilityKey, record.capabilityLabel, record.providerPluginPreviewImageBase64, record.providerPluginName)}
            {isMobile && (
                <Space direction="vertical" size={4}>
                    <Text type="secondary" style={{fontSize: 12}}>
                        {textOrEmpty(record.serviceLabel) || record.serviceName}
                    </Text>
                    {statusTags(record.selected, record.reviewRequired, record.confirmed)}
                </Space>
            )}
        </Space>
    );

    const serviceColumns: ColumnsType<ServiceProviderRow> = [
        {
            title: "服务",
            key: "service",
            width: 180,
            responsive: ["md"],
            render: (_, record) => serviceNameCell(record)
        },
        {
            title: "来源插件",
            key: "provider",
            render: (_, record) => serviceProviderCell(record)
        },
        {
            title: "状态",
            key: "status",
            width: 180,
            responsive: ["md"],
            render: (_, record) => statusTags(record.selected, record.reviewRequired, record.confirmed)
        },
        {
            title: "操作",
            key: "action",
            width: isMobile ? 96 : 190,
            render: (_, record) => (
                <Space size={isMobile ? 2 : "small"}>
                    <Tooltip title="设为默认">
                        <Button type={isMobile ? "text" : "link"} size="small" icon={<CheckOutlined />} aria-label="设为默认" disabled={record.confirmed} onClick={() => setDefaultProvider(record)}>
                            {!isMobile && "设为默认"}
                        </Button>
                    </Tooltip>
                    <Tooltip title="自动选择">
                        <Button type={isMobile ? "text" : "link"} size="small" icon={<ReloadOutlined />} aria-label="自动选择" onClick={() => restoreAutoProvider(record.serviceName)}>
                            {!isMobile && "自动选择"}
                        </Button>
                    </Tooltip>
                </Space>
            )
        }
    ];
    const commentProviderCell = (record: CommentProviderRow) => (
        <Space direction="vertical" size={8} style={{width: "100%", minWidth: 0}}>
            {renderPluginIdentity({
                title: textOrEmpty(record.pluginName) || "未命名插件",
                subtitle: textOrEmpty(record.description) || "评论插件",
                pluginPreviewImageBase64: record.pluginPreviewImageBase64
            })}
            {isMobile && statusTags(record.selected, record.reviewRequired, record.confirmed)}
        </Space>
    );

    const commentColumns: ColumnsType<CommentProviderRow> = [
        {
            title: "类型",
            key: "type",
            width: 180,
            responsive: ["md"],
            render: () => <Text strong>评论插件</Text>
        },
        {
            title: "来源插件",
            key: "provider",
            render: (_, record) => commentProviderCell(record)
        },
        {
            title: "状态",
            key: "status",
            width: 180,
            responsive: ["md"],
            render: (_, record) => statusTags(record.selected, record.reviewRequired, record.confirmed)
        },
        {
            title: "操作",
            key: "action",
            width: isMobile ? 96 : 190,
            render: (_, record) => (
                <Space size={isMobile ? 2 : "small"}>
                    <Tooltip title="设为当前">
                        <Button type={isMobile ? "text" : "link"} size="small" icon={<CheckOutlined />} aria-label="设为当前" disabled={record.confirmed} onClick={() => setCommentProvider(record)}>
                            {!isMobile && "设为当前"}
                        </Button>
                    </Tooltip>
                    <Tooltip title="恢复默认">
                        <Button type={isMobile ? "text" : "link"} size="small" icon={<ReloadOutlined />} aria-label="恢复默认" onClick={restoreDefaultCommentProvider}>
                            {!isMobile && "恢复默认"}
                        </Button>
                    </Tooltip>
                </Space>
            )
        }
    ];

    return (
        <Space direction="vertical" size={16} style={{width: "100%"}}>
            {contextHolder}
            <Text strong>系统服务</Text>
            <Table<ServiceProviderRow> loading={loading} rowKey={record => `${record.serviceName}:${record.providerPluginId}:${record.capabilityKey}`} columns={serviceColumns} dataSource={providers} pagination={false} scroll={isMobile ? undefined : {x: 760}} />
            <Text strong>评论插件</Text>
            <Table<CommentProviderRow> loading={loading} rowKey={record => record.shortName} columns={commentColumns} dataSource={commentProviders} pagination={false} scroll={isMobile ? undefined : {x: 760}} />
        </Space>
    );
};

export default ServiceRuntimeTab;
