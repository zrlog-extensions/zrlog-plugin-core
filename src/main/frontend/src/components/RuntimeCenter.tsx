import React from "react";
import {Button, Grid, Space, Tabs, Tooltip, Typography} from "antd";
import {ArrowLeftOutlined} from "@ant-design/icons";
import {Content} from "antd/es/layout/layout";
import {useLocation, useNavigate} from "react-router-dom";
import {PluginCoreInfoResponse} from "../index";
import SchedulerRuntimeTab from "./runtime/SchedulerRuntimeTab";
import RuntimeStatesTab from "./runtime/RuntimeStatesTab";
import NotificationRuntimeTab from "./runtime/NotificationRuntimeTab";
import ServiceRuntimeTab from "./runtime/ServiceRuntimeTab";
import {backPath, RuntimeTab, runtimeTabFromPath, runtimeTabPath} from "./runtime/common";

const {Title, Text} = Typography;

type RuntimeCenterProps = {
    data: PluginCoreInfoResponse;
}

const RuntimeCenter: React.FC<RuntimeCenterProps> = ({data}) => {
    const location = useLocation();
    const navigate = useNavigate();
    const screens = Grid.useBreakpoint();
    const isMobile = Boolean((screens.xs || screens.sm) && !screens.md);
    const activeTab = runtimeTabFromPath(location.pathname);

    return (
        <Content style={{padding: isMobile ? "12px 12px 20px" : "20px 20px 24px 20px"}}>
            <Space direction="vertical" size={isMobile ? 12 : 18} style={{width: "100%"}}>
                <div style={{display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap"}}>
                    <Space align="start">
                        <Tooltip title="返回插件列表">
                            <Button
                                type="text"
                                icon={<ArrowLeftOutlined />}
                                aria-label="返回插件列表"
                                onClick={() => navigate(backPath())}
                                style={{marginTop: -1}}
                            />
                        </Tooltip>
                        <div>
                            <Title level={2} style={{margin: 0, fontSize: isMobile ? 20 : 22}}>插件运行时</Title>
                            {!isMobile && <Text type="secondary">按需加载、调度、通知与调用状态</Text>}
                        </div>
                    </Space>
                </div>

                <Tabs
                    activeKey={activeTab}
                    size={isMobile ? "small" : "middle"}
                    tabBarGutter={isMobile ? 12 : 32}
                    onChange={key => navigate(runtimeTabPath(key as RuntimeTab))}
                    items={[
                        {
                            key: "scheduler",
                            label: "调度中心",
                            children: <SchedulerRuntimeTab dark={data.dark} />
                        },
                        {
                            key: "runtime",
                            label: "运行态",
                            children: <RuntimeStatesTab dark={data.dark} />
                        },
                        {
                            key: "notification",
                            label: "通知",
                            children: <NotificationRuntimeTab />
                        },
                        {
                            key: "services",
                            label: "服务",
                            children: <ServiceRuntimeTab />
                        }
                    ]}
                />
            </Space>
        </Content>
    );
};

export default RuntimeCenter;
