import React from "react";
import { Result, Button } from "antd";

interface UnknownErrorPageProps {
    message: string;
}

const UnknownErrorPage: React.FC<UnknownErrorPageProps> = ({ message }) => {
    const getSecondTitle = () => message;

    return (
        <Result
            status="500"
            title="500"
            subTitle={getSecondTitle()}
            extra={<Button type="primary">Unknown Error</Button>}
        />
    );
};

export default UnknownErrorPage;