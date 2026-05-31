import React, {useEffect, useState} from "react";
import {Button, Grid, Space, Table, Tag, Tooltip, Typography, message} from "antd";
import {CheckOutlined, ReloadOutlined} from "@ant-design/icons";
import type {ColumnsType} from "antd/es/table";
import axios from "axios";
import {apiPath, Capability, formatEpoch, formatTime, NotificationDelivery, NotificationProviderRow, paginationFromResponse, RuntimePagination, useCapabilityView} from "./common";

const {Text} = Typography;

const NotificationRuntimeTab: React.FC = () => {
    const screens = Grid.useBreakpoint();
    const isMobile = Boolean((screens.xs || screens.sm) && !screens.md);
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});
    const [loading, setLoading] = useState(false);
    const [capabilities, setCapabilities] = useState<Capability[]>([]);
    const [providers, setProviders] = useState<NotificationProviderRow[]>([]);
    const [deliveries, setDeliveries] = useState<NotificationDelivery[]>([]);
    const [deliveryPagination, setDeliveryPagination] = useState<RuntimePagination>({
        current: 1,
        pageSize: 8,
        total: 0
    });
    const {renderCapability} = useCapabilityView(capabilities);

    const loadData = async (deliveryPage = deliveryPagination.current, deliveryPageSize = deliveryPagination.pageSize) => {
        setLoading(true);
        try {
            const [capabilitiesRes, providersRes, deliveriesRes] = await Promise.all([
                axios.get(apiPath("/runtime-capabilities")),
                axios.get(apiPath("/runtime-notification/channels")),
                axios.get(apiPath("/runtime-notification/deliveries"), {params: {page: deliveryPage, pageSize: deliveryPageSize}})
            ]);
            setCapabilities(capabilitiesRes.data.items || []);
            setProviders(providersRes.data.items || []);
            setDeliveries(deliveriesRes.data.items || []);
            setDeliveryPagination(paginationFromResponse<NotificationDelivery>(deliveriesRes.data, {
                current: deliveryPage,
                pageSize: deliveryPageSize,
                total: 0
            }));
        } catch (e) {
            messageApi.error("通知数据加载失败");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const setDefaultProvider = async (row: NotificationProviderRow) => {
        const params = new URLSearchParams();
        params.set("channel", row.channel);
        params.set("pluginId", row.providerPluginId);
        params.set("capabilityKey", row.capabilityKey);
        const {data: resp} = await axios.post(apiPath("/runtime-notification/provider"), params.toString());
        if (resp.code > 0) {
            messageApi.error(resp.message);
            return;
        }
        messageApi.success("已保存");
        await loadData(deliveryPagination.current, deliveryPagination.pageSize);
    };

    const restoreAutoProvider = async (channel: string) => {
        const params = new URLSearchParams();
        params.set("channel", channel);
        const {data: resp} = await axios.post(apiPath("/runtime-notification/provider/auto"), params.toString());
        if (resp.code > 0) {
            messageApi.error(resp.message);
            return;
        }
        messageApi.success("已恢复自动选择");
        await loadData(deliveryPagination.current, deliveryPagination.pageSize);
    };

    const statusTags = (selected: boolean, reviewRequired: boolean, confirmed: boolean) => (
        <Space size={[4, 4]} wrap>
            {selected && <Tag color="success">默认</Tag>}
            {reviewRequired && <Tag color="warning">需确认</Tag>}
            {confirmed && <Tag color="blue">已确认</Tag>}
        </Space>
    );
    const providerCell = (record: NotificationProviderRow) => (
        <Space direction="vertical" size={8} style={{width: "100%", minWidth: 0}}>
            {renderCapability(record.providerPluginId, record.capabilityKey, record.capabilityLabel, record.providerPluginPreviewImageBase64, record.providerPluginName)}
            {isMobile && (
                <Space direction="vertical" size={4}>
                    <Text type="secondary" style={{fontSize: 12}}>通道 {record.channel}</Text>
                    {statusTags(record.selected, record.reviewRequired, record.confirmed)}
                </Space>
            )}
        </Space>
    );

    const providerColumns: ColumnsType<NotificationProviderRow> = [
        {title: "通道", dataIndex: "channel", width: 120, responsive: ["md"]},
        {
            title: "Provider",
            key: "provider",
            render: (_, record) => providerCell(record)
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
                        <Button type={isMobile ? "text" : "link"} size="small" icon={<ReloadOutlined />} aria-label="自动选择" onClick={() => restoreAutoProvider(record.channel)}>
                            {!isMobile && "自动选择"}
                        </Button>
                    </Tooltip>
                </Space>
            )
        }
    ];

    const deliveryStatusTag = (value: string) => value === "success" ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>;
    const deliveryCell = (record: NotificationDelivery) => (
        <Space direction="vertical" size={8} style={{width: "100%", minWidth: 0}}>
            {renderCapability(record.providerPluginId, record.capabilityKey, undefined, record.providerPluginPreviewImageBase64, record.providerPluginName)}
            {isMobile && (
                <Space direction="vertical" size={4} style={{width: "100%"}}>
                    <Space size={[4, 4]} wrap>
                        {deliveryStatusTag(record.status)}
                        <Tag>{record.channel}</Tag>
                    </Space>
                    <Text type="secondary" style={{fontSize: 12}}>{formatEpoch(record.createdAt)}</Text>
                    {record.errorMessage && (
                        <Text type="danger" ellipsis style={{fontSize: 12, maxWidth: "100%"}}>
                            {record.errorMessage}
                        </Text>
                    )}
                </Space>
            )}
        </Space>
    );

    const deliveryColumns: ColumnsType<NotificationDelivery> = [
        {title: "通道", dataIndex: "channel", width: 120, responsive: ["md"]},
        {title: "能力", key: "capability", render: (_, record) => deliveryCell(record)},
        {
            title: "状态",
            dataIndex: "status",
            width: 100,
            responsive: ["md"],
            render: deliveryStatusTag
        },
        {title: "时间", dataIndex: "createdAt", width: 240, render: formatEpoch, responsive: ["md"]},
        {title: "错误", dataIndex: "errorMessage", render: formatTime, responsive: ["md"]}
    ];

    return (
        <Space direction="vertical" size={16} style={{width: "100%"}}>
            {contextHolder}
            <Table<NotificationProviderRow> loading={loading} rowKey={record => `${record.channel}:${record.providerPluginId}:${record.capabilityKey}`} columns={providerColumns} dataSource={providers} pagination={false} scroll={isMobile ? undefined : {x: 760}} />
            <Text strong>最近投递</Text>
            <Table<NotificationDelivery>
                loading={loading}
                rowKey="id"
                columns={deliveryColumns}
                dataSource={deliveries}
                pagination={{...deliveryPagination, showSizeChanger: !isMobile}}
                onChange={pagination => loadData(pagination.current || 1, pagination.pageSize || 8)}
                scroll={isMobile ? undefined : {x: 760}}
            />
        </Space>
    );
};

export default NotificationRuntimeTab;
