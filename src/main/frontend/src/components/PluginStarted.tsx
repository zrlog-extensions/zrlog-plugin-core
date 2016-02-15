import React from "react";
import { Result, Button } from "antd";

const PluginStarted: React.FC = () => {
    return (
        <Result
            status="error"
            title="插件已经在运行了"
            subTitle=""
            extra={<Button type="primary">Go Back</Button>}
        />
    );
};

export default PluginStarted;