import React, {useEffect, useState} from "react";
import {
    Button,
    Card,
    Col,
    Empty,
    Image,
    message,
    Popconfirm,
    Row,
    Tag,
    Tooltip,
    Space,
    Typography,
    Segmented,
    Input,
    Table,
    Grid
} from "antd";
import type {TableColumnsType} from "antd";
import {
    CloudDownloadOutlined,
    DeleteOutlined,
    SettingOutlined,
    SafetyCertificateOutlined,
    AppstoreOutlined,
    ApiOutlined,
    CompassOutlined,
    GlobalOutlined,
    UnorderedListOutlined,
    SearchOutlined,
    FieldTimeOutlined
} from "@ant-design/icons";
import axios from "axios";
import {Link, useNavigate} from "react-router-dom";
import {Content} from "antd/es/layout/layout";
import {Plugin, PluginCoreInfoResponse} from "../index";
import Settings from "./Settings";

const { Title, Text, Paragraph } = Typography;

function getFillBackImg(): string {
    return 'data:image/png;base64,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'
}

const runtimePath = () => {
    if (window.location.pathname.startsWith("/admin/plugins")) {
        return "/admin/plugins/runtime-scheduler";
    }
    if (window.location.pathname.startsWith("/p/") || window.location.pathname === "/p") {
        return "/p/runtime-scheduler";
    }
    if (window.location.pathname.startsWith("/plugin/") || window.location.pathname === "/plugin") {
        return "/plugin/runtime-scheduler";
    }
    return "/runtime-scheduler";
};

type CoreIndexProps = {
    data: PluginCoreInfoResponse;
    onRefresh: () => Promise<void>;
}

type ServiceDisplay = {
    key: string;
    label: string;
}

const displayServices = (plugin: Plugin): ServiceDisplay[] => {
    if (plugin.capabilities && plugin.capabilities.length > 0) {
        return plugin.capabilities.map(capability => ({
            key: capability.key,
            label: capability.label && capability.label.trim().length > 0 ? capability.label : capability.key
        }));
    }
    return (plugin.services || []).map(service => ({
        key: service,
        label: service
    }));
};

const compactLabel = (value: string, maxLength = 8) => value.length > maxLength ? `${value.substring(0, maxLength)}...` : value;
const pluginNameText = (plugin: Plugin) => {
    const name = plugin.name && plugin.name.trim().length > 0 ? plugin.name.trim() : "";
    return name || plugin.shortName;
};

const CustomStyles: React.FC<{dark: boolean}> = ({dark}) => (
    <style dangerouslySetInnerHTML={{__html: `
        .plugin-card-hover {
            transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
            border-radius: 12px !important;
            overflow: hidden;
        }
        .plugin-card-hover:hover {
            transform: translateY(-6px);
            box-shadow: 0 12px 24px -10px rgba(0, 0, 0, 0.12), 0 4px 20px 0 rgba(0, 0, 0, 0.06);
        }
        .plugin-image-container {
            width: 100%;
            aspect-ratio: 1 / 1;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
            position: relative;
            padding: 0;
        }
        .plugin-image-zoom {
            transition: transform 0.5s ease;
        }
        .plugin-card-hover:hover .plugin-image-zoom {
            transform: scale(1.05);
        }
        .plugin-list-info {
            max-width: 100%;
        }
        .plugin-list-info .ant-space-item:last-child {
            min-width: 0;
        }
        .header-card {
            background: linear-gradient(135deg, rgba(22, 119, 255, 0.05) 0%, rgba(22, 119, 255, 0.01) 100%);
            border: 1px solid rgba(22, 119, 255, 0.08);
            border-radius: 16px;
            padding: 24px;
            margin-bottom: 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 16px;
        }
        .dark-mode-header {
            background: linear-gradient(135deg, rgba(22, 119, 255, 0.12) 0%, rgba(22, 119, 255, 0.04) 100%) !important;
            border: 1px solid rgba(22, 119, 255, 0.15) !important;
        }
        .market-banner {
            background: linear-gradient(135deg, rgba(22, 119, 255, 0.06) 0%, rgba(114, 46, 209, 0.03) 100%);
            border: 1px solid rgba(22, 119, 255, 0.1);
            border-radius: 16px;
            padding: 32px;
            margin-top: 32px;
            margin-bottom: 32px;
            text-align: center;
            transition: all 0.3s ease;
        }
        .market-banner:hover {
            box-shadow: 0 8px 24px rgba(22, 119, 255, 0.06);
            border-color: rgba(22, 119, 255, 0.18);
        }
        .dark-mode-banner {
            background: linear-gradient(135deg, rgba(22, 119, 255, 0.1) 0%, rgba(114, 46, 209, 0.05) 100%) !important;
            border-color: rgba(22, 119, 255, 0.15) !important;
        }
        .plugin-table .ant-table-thead > tr > th {
            background: transparent !important;
            font-weight: 600;
            border-bottom: 1px solid ${dark ? '#303030' : '#f0f0f0'} !important;
        }
        .plugin-table .ant-table-row {
            transition: background 0.2s ease;
        }
        .plugin-table .ant-table-cell {
            border-bottom: 1px solid ${dark ? '#222222' : '#fafafa'} !important;
            padding: 16px 12px !important;
        }
        @media (max-width: 767px) {
            .header-card {
                padding: 16px;
                border-radius: 12px;
                align-items: flex-start;
            }
            .plugin-table .ant-table-cell {
                padding: 12px 10px !important;
            }
            .plugin-table .ant-table-thead > tr > th {
                padding: 12px 10px !important;
            }
            .plugin-list-desc {
                max-width: calc(100vw - 190px) !important;
            }
            .plugin-list-name {
                display: inline-block;
                max-width: calc(100vw - 220px);
                overflow: hidden;
                text-overflow: ellipsis;
                vertical-align: bottom;
                white-space: nowrap;
            }
            .plugin-card-hover:hover {
                transform: none;
            }
        }
    `}} />
);

const CoreIndex: React.FC<CoreIndexProps> = ({data, onRefresh}) => {

    const navigate = useNavigate();
    const screens = Grid.useBreakpoint();
    const isMobile = Boolean((screens.xs || screens.sm) && !screens.md);
    const isCompact = !screens.lg;
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});
    const [searchText, setSearchText] = useState("");
    const [viewType, setViewType] = useState<'grid' | 'list'>(() => {
        return (localStorage.getItem('zrlog-plugin-view-type') as 'grid' | 'list') || 'list';
    });
    useEffect(() => {
        onRefresh();
    }, [onRefresh]);

    const deletePlugin = (pluginName: string) => {
        axios.get("api/uninstall?name=" + pluginName).then(({data}) => {
            if (data.code > 0) {
                messageApi.error(data.message);
                return;
            }
            onRefresh();
        });
    };

    const isRequired = (pluginName: string) => {
        return data.requiredPlugins && data.requiredPlugins.includes(pluginName);
    };

    const filteredPlugins = data.plugins.filter(plugin => {
        const keyword = searchText.toLowerCase();
        const services = displayServices(plugin);
        return pluginNameText(plugin).toLowerCase().includes(keyword) ||
            (plugin.desc && plugin.desc.toLowerCase().includes(keyword)) ||
            plugin.shortName.toLowerCase().includes(keyword) ||
            services.some(service =>
                service.label.toLowerCase().includes(keyword) ||
                service.key.toLowerCase().includes(keyword)
            );
    });

    const columns: TableColumnsType<Plugin> = [
        {
            title: '插件信息',
            key: 'plugin',
            render: (_: unknown, plugin: Plugin) => {
                const services = displayServices(plugin);
                return (
                <Space className="plugin-list-info" size="middle" align="start">
                    <div style={{
                        width: 44,
                        height: 44,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: data.dark ? '#1f1f1f' : '#fcfcfc',
                        borderRadius: 8,
                        overflow: 'hidden',
                        padding: 2,
                        border: `1px solid ${data.dark ? '#303030' : '#e8e8e8'}`,
                        flexShrink: 0
                    }}>
                        {!plugin.previewImageBase64 ? (
                            <div style={{
                                width: '100%',
                                height: '100%',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                background: data.dark ? '#141414' : '#f5f5f5',
                                color: data.primaryColor,
                                borderRadius: 6
                            }}>
                                <ApiOutlined style={{ fontSize: '18px', opacity: 0.8 }} />
                            </div>
                        ) : (
                            <Image
                                width="100%"
                                height="100%"
                                style={{ objectFit: 'contain' }}
                                preview={false}
                                fallback={getFillBackImg()}
                                src={plugin.previewImageBase64}
                            />
                        )}
                    </div>
                    <div style={{minWidth: 0}}>
                        <Space align="center" size={8} style={{ flexWrap: 'wrap' }}>
                            <span className="plugin-list-name" style={{ fontWeight: 600, fontSize: '14px', color: data.dark ? 'rgba(255,255,255,0.85)' : 'rgba(0,0,0,0.85)' }}>
                                {pluginNameText(plugin)}
                            </span>
                            {isRequired(plugin.shortName) && (
                                <Tag color="gold" bordered={false} style={{ fontSize: '10px', margin: 0, padding: '0 4px', lineHeight: '14px' }}>
                                    系统
                                </Tag>
                            )}
                        </Space>
                        <div className="plugin-list-desc" style={{
                            fontSize: '12px', 
                            color: data.dark ? 'rgba(255,255,255,0.45)' : 'rgba(0,0,0,0.45)',
                            marginTop: 4,
                            maxWidth: 500,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap'
                        }} title={plugin.desc}>
                            {plugin.desc || '暂无描述。'}
                        </div>
                        {isMobile && services.length > 0 && (
                            <Text type="secondary" style={{fontSize: 12, display: "block", marginTop: 8}}>
                                服务 {services.length}
                            </Text>
                        )}
                    </div>
                </Space>
                );
            }
        },
        {
            title: '提供服务',
            key: 'services',
            responsive: ['md'],
            render: (_: any, plugin: Plugin) => {
                const services = displayServices(plugin);
                if (services.length === 0) {
                    return <span style={{ color: data.dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.25)', fontSize: '12px' }}>-</span>;
                }
                return (
                    <Space size={4} wrap>
                        {services.map(service => (
                            <Tooltip title={service.key} key={service.key}>
                                <Tag color="purple" bordered={false} style={{ fontSize: '10px', margin: 0 }}>
                                    {service.label}
                                </Tag>
                            </Tooltip>
                        ))}
                    </Space>
                );
            }
        },
        {
            title: '操作',
            key: 'actions',
            width: isMobile ? 86 : 140,
            align: 'right' as const,
            render: (_: unknown, plugin: Plugin) => {
                const isReq = isRequired(plugin.shortName);
                return (
                    <Space size={isMobile ? 10 : "middle"}>
                        <Tooltip title={isMobile ? "配置" : undefined}>
                            <a href={plugin.shortName + "/"} aria-label="配置" style={{ color: data.primaryColor, fontWeight: 500, fontSize: '13px', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                <SettingOutlined /> {!isMobile && '配置'}
                            </a>
                        </Tooltip>
                        {isReq ? (
                            <Tooltip title="此插件为系统必要插件，无法卸载">
                                <span aria-label="卸载" style={{ color: data.dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.25)', cursor: 'not-allowed', fontSize: '13px', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                    <DeleteOutlined /> {!isMobile && '卸载'}
                                </span>
                            </Tooltip>
                        ) : (
                            <Popconfirm
                                title={`确定卸载插件《${pluginNameText(plugin)}》吗？`}
                                description="卸载后相关功能将被停用。"
                                okText="确定"
                                okButtonProps={{ danger: true }}
                                onConfirm={() => deletePlugin(plugin.shortName)}
                                cancelText="取消"
                            >
                                <Tooltip title={isMobile ? "卸载" : undefined}>
                                    <Link to={"#"} aria-label="卸载" style={{ color: '#ff4d4f', fontWeight: 500, fontSize: '13px', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                        <DeleteOutlined /> {!isMobile && '卸载'}
                                    </Link>
                                </Tooltip>
                            </Popconfirm>
                        )}
                    </Space>
                );
            }
        }
    ];

    return (
        <Content style={{ padding: isMobile ? '12px 12px 20px' : isCompact ? '16px' : '20px 20px 24px 20px' }}>
            {contextHolder}
            <CustomStyles dark={data.dark} />
            
            {/* Dashboard Header */}
            <div className={`header-card ${data.dark ? 'dark-mode-header' : ''}`}>
                <div>
                    <Space align="center" size="middle">
                        <ApiOutlined style={{ fontSize: '30px', color: data.primaryColor }} />
                        <div>
                            <Title level={2} style={{ margin: 0, fontWeight: 700, fontSize: '22px' }}>
                                插件管理中心
                            </Title>
                            <Text type="secondary" style={{ fontSize: '13px', display: 'block', marginTop: '2px' }}>
                                管理插件安装、配置、运行状态和扩展功能
                            </Text>
                        </div>
                    </Space>
                </div>
                <Space size="middle" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
                    <div style={{ textAlign: 'right', marginRight: '16px' }}>
                        <div style={{ fontSize: '11px', opacity: 0.6 }}>已安装插件</div>
                        <Tag color="success" style={{ margin: 0, borderRadius: '4px', fontWeight: 600 }}>
                            {data.plugins.length} 个
                        </Tag>
                    </div>
                    <div style={{
                        width: 1,
                        height: 30,
                        background: data.dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)',
                        margin: '0 2px'
                    }} />
                    <Button
                        type="primary"
                        icon={<FieldTimeOutlined />}
                        onClick={() => navigate(runtimePath())}
                        style={{
                            borderRadius: '8px',
                            height: '36px',
                            fontWeight: 600,
                            boxShadow: '0 4px 10px rgba(22, 119, 255, 0.18)'
                        }}
                    >
                        运行时控制台
                    </Button>
                    <Tooltip title="插件设置">
                        <span>
                            <Settings style={{
                                borderRadius: '8px',
                                width: '36px',
                                height: '36px',
                                padding: 0,
                                display: 'inline-flex',
                                alignItems: 'center',
                                justifyContent: 'center'
                            }} setting={data.setting}>
                                <SettingOutlined />
                            </Settings>
                        </span>
                    </Tooltip>
                </Space>
            </div>

            {/* Empty State */}
            {data.plugins.length === 0 && (
                <div style={{ 
                    padding: '80px 0', 
                    background: data.dark ? 'rgba(255,255,255,0.01)' : 'rgba(0,0,0,0.01)', 
                    borderRadius: '16px',
                    border: `1px dashed ${data.dark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)'}`
                }}>
                    <Empty 
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                            <span style={{ fontSize: '14px', color: data.dark ? 'rgba(255,255,255,0.45)' : 'rgba(0,0,0,0.45)' }}>
                                目前还没有安装任何插件
                            </span>
                        }
                    >
                        <a href={data.pluginCenter}>
                            <Button 
                                icon={<CompassOutlined />} 
                                type="primary"
                                size="large"
                                style={{ 
                                    borderRadius: '8px', 
                                    height: '40px',
                                    fontWeight: 600,
                                    boxShadow: '0 4px 10px rgba(22, 119, 255, 0.2)' 
                                }}
                            >
                                去插件市场下载
                            </Button>
                        </a>
                    </Empty>
                </div>
            )}

            {/* Plugin List Content */}
            {data.plugins.length > 0 && (
                <div>
                    {/* Toolbar */}
                    <div style={{ 
                        display: 'flex', 
                        justifyContent: 'space-between', 
                        alignItems: 'center',
                        marginBottom: 16,
                        gap: 12,
                        flexWrap: 'wrap'
                    }}>
                        <Input
                            placeholder="搜索已安装插件..."
                            prefix={<SearchOutlined style={{ color: data.dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.25)' }} />}
                            value={searchText}
                            onChange={e => setSearchText(e.target.value)}
                            style={{ width: isMobile ? '100%' : 260, borderRadius: '8px', height: '36px' }}
                            allowClear
                        />
                        <Segmented
                            value={viewType}
                            onChange={(value) => {
                                setViewType(value as 'grid' | 'list');
                                localStorage.setItem('zrlog-plugin-view-type', value as string);
                            }}
                            options={[
                                { value: 'list', icon: <UnorderedListOutlined />, label: '列表' },
                                { value: 'grid', icon: <AppstoreOutlined />, label: '网格' }
                            ]}
                            style={{ borderRadius: '8px' }}
                        />
                    </div>

                    {/* Empty search results */}
                    {filteredPlugins.length === 0 ? (
                        <div style={{ 
                            padding: '60px 0', 
                            background: data.dark ? 'rgba(255,255,255,0.01)' : 'rgba(0,0,0,0.01)', 
                            borderRadius: '16px',
                            border: `1px dashed ${data.dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.05)'}`,
                            textAlign: 'center'
                        }}>
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有找到符合搜索条件的插件" />
                        </div>
                    ) : (
                        <div>
                            {/* List View */}
                            {viewType === 'list' && (
                                <Table
                                    className="plugin-table"
                                    columns={columns}
                                    dataSource={filteredPlugins}
                                    rowKey="shortName"
                                    pagination={false}
                                    style={{
                                        background: data.dark ? '#141414' : '#ffffff',
                                        border: `1px solid ${data.dark ? '#303030' : '#f0f0f0'}`,
                                        borderRadius: '12px',
                                        overflow: 'hidden'
                                    }}
                                />
                            )}

                            {/* Grid View */}
                            {viewType === 'grid' && (
                                <Row gutter={[isCompact ? 16 : 20, isCompact ? 16 : 20]} style={{ marginLeft: 0, marginRight: 0 }}>
                                    {filteredPlugins.map((plugin) => {
                                        const isReq = isRequired(plugin.shortName);
                                        const services = displayServices(plugin);
                                        
                                        return (
                                            <Col md={12} lg={6} xxl={4} xs={24} sm={12} key={plugin.shortName}>
                                                <Card
                                                    className="plugin-card-hover"
                                                    style={{
                                                        border: `1px solid ${data.dark ? '#303030' : '#f0f0f0'}`,
                                                        background: data.dark ? '#141414' : '#ffffff'
                                                    }}
                                                    cover={
                                                        <div className="plugin-image-container" style={{ backgroundColor: data.dark ? '#1d1d1d' : '#fcfcfc' }}>
                                                            {/* Required/System Badge */}
                                                            {isReq && (
                                                                <div style={{
                                                                    position: 'absolute',
                                                                    top: 12,
                                                                    left: 12,
                                                                    zIndex: 10,
                                                                }}>
                                                                    <Tag color="gold" style={{ margin: 0, fontWeight: 600, border: 'none', borderRadius: '4px' }}>
                                                                        <SafetyCertificateOutlined style={{ marginRight: '4px' }} />
                                                                        系统
                                                                    </Tag>
                                                                </div>
                                                            )}

                                                            {!plugin.previewImageBase64 ? (
                                                                <div style={{
                                                                    width: '100%',
                                                                    height: '100%',
                                                                    display: 'flex',
                                                                    flexDirection: 'column',
                                                                    alignItems: 'center',
                                                                    justifyContent: 'center',
                                                                    background: data.dark ? 'linear-gradient(135deg, #1f1f1f 0%, #141414 100%)' : 'linear-gradient(135deg, #f5f5f5 0%, #eaeaea 100%)',
                                                                    color: data.dark ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.15)',
                                                                    gap: '8px'
                                                                }}>
                                                                    <ApiOutlined style={{ fontSize: '38px', color: data.primaryColor, opacity: 0.6 }} />
                                                                    <span style={{ fontSize: '11px', fontWeight: 500, opacity: 0.7, color: data.dark ? 'rgba(255,255,255,0.45)' : 'rgba(0,0,0,0.45)' }}>插件</span>
                                                                </div>
                                                            ) : (
                                                                <Image
                                                                    preview={false}
                                                                    fallback={getFillBackImg()}
                                                                    className="plugin-image-zoom"
                                                                    style={{ 
                                                                        width: '100%', 
                                                                        height: '100%',
                                                                        objectFit: 'cover'
                                                                    }}
                                                                    alt={pluginNameText(plugin)}
                                                                    src={plugin.previewImageBase64}
                                                                />
                                                            )}
                                                        </div>
                                                    }
                                                    actions={[
                                                        <a href={plugin.shortName + "/"} style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px', height: '100%', color: data.primaryColor, fontWeight: 600 }}>
                                                            <SettingOutlined key="preview" /> 配置
                                                        </a>,
                                                        isReq ? (
                                                            <Tooltip title="此插件为系统必要插件，无法卸载" key="delete-disabled">
                                                                <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px', height: '100%', color: '#bfbfbf', cursor: 'not-allowed', width: '100%' }}>
                                                                    <DeleteOutlined /> 卸载
                                                                </span>
                                                            </Tooltip>
                                                        ) : (
                                                            <Popconfirm
                                                                title={`确定卸载插件《${pluginNameText(plugin)}》吗？`}
                                                                description="卸载后相关功能将被停用。"
                                                                okText="确定"
                                                                okButtonProps={{ danger: true }}
                                                                onConfirm={() => {
                                                                    deletePlugin(plugin.shortName)
                                                                }}
                                                                cancelText="取消"
                                                                key="delete"
                                                            >
                                                                <Link to={"#"} style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '6px', height: '100%', color: '#ff4d4f', fontWeight: 600 }}>
                                                                    <DeleteOutlined /> 卸载
                                                                </Link>
                                                            </Popconfirm>
                                                        )
                                                    ]}
                                                >
                                                    <div style={{ minHeight: '90px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                                                        <div>
                                                            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '8px', gap: '4px' }}>
                                                                <span style={{ fontWeight: 600, fontSize: '15px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1, color: data.dark ? 'rgba(255,255,255,0.85)' : 'rgba(0,0,0,0.85)' }} title={plugin.shortName}>
                                                                    {pluginNameText(plugin)}
                                                                </span>
                                                            </div>
                                                            <Paragraph style={{
                                                                fontSize: '13px',
                                                                color: data.dark ? 'rgba(255, 255, 255, 0.45)' : 'rgba(0, 0, 0, 0.45)',
                                                                margin: '0 0 12px 0',
                                                                overflow: 'hidden',
                                                                textOverflow: 'ellipsis',
                                                                display: '-webkit-box',
                                                                WebkitLineClamp: '2',
                                                                WebkitBoxOrient: 'vertical',
                                                                height: '38px',
                                                                lineHeight: '19px'
                                                            }}>
                                                                {plugin.desc || '该插件暂无详细描述。'}
                                                            </Paragraph>
                                                        </div>

                                                        {services.length > 0 && (
                                                            <div style={{ borderTop: `1px dashed ${data.dark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.06)'}`, paddingTop: '10px', marginTop: 'auto', display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                                                                <GlobalOutlined style={{ fontSize: '11px', color: data.dark ? 'rgba(255, 255, 255, 0.45)' : 'rgba(0, 0, 0, 0.45)' }} />
                                                                <span style={{ fontSize: '11px', color: data.dark ? 'rgba(255, 255, 255, 0.45)' : 'rgba(0, 0, 0, 0.45)', marginRight: '2px' }}>服务:</span>
                                                                {services.slice(0, 2).map(service => (
                                                                    <Tooltip title={service.key} key={service.key}>
                                                                        <Tag color="purple" bordered={false} style={{ fontSize: '10px', margin: 0, padding: '0 4px', lineHeight: '14px', borderRadius: '2px' }}>
                                                                            {compactLabel(service.label)}
                                                                        </Tag>
                                                                    </Tooltip>
                                                                ))}
                                                                {services.length > 2 && (
                                                                    <Tag color="default" bordered={false} style={{ fontSize: '10px', margin: 0, padding: '0 4px', lineHeight: '14px', borderRadius: '2px' }}>
                                                                        +{services.length - 2}
                                                                    </Tag>
                                                                )}
                                                            </div>
                                                        )}
                                                    </div>
                                                </Card>
                                            </Col>
                                        );
                                    })}
                                </Row>
                            )}
                        </div>
                    )}

                    {/* Promotion Banner */}
                    <div className={`market-banner ${data.dark ? 'dark-mode-banner' : ''}`}>
                        <Title level={3} style={{ margin: '0 0 8px 0', fontWeight: 600, fontSize: '16px', color: data.dark ? 'rgba(255,255,255,0.85)' : 'rgba(0,0,0,0.85)' }}>
                            获取更多插件
                        </Title>
                        <Paragraph style={{ 
                            color: data.dark ? 'rgba(255, 255, 255, 0.45)' : 'rgba(0, 0, 0, 0.45)', 
                            fontSize: '13px',
                            marginBottom: '20px'
                        }}>
                            可以前往插件市场浏览和下载更多扩展插件。
                        </Paragraph>
                        <a href={data.pluginCenter}>
                            <Button 
                                icon={<CloudDownloadOutlined />} 
                                type="primary" 
                                size="large"
                                style={{ 
                                    borderRadius: '8px',
                                    height: '38px',
                                    padding: '0 24px',
                                    fontWeight: 600,
                                    boxShadow: '0 4px 10px rgba(22, 119, 255, 0.2)'
                                }}
                            >
                                前往插件市场
                            </Button>
                        </a>
                    </div>
                </div>
            )}
        </Content>
    );
};

export default CoreIndex;
