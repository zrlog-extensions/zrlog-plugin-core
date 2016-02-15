import React from "react";
import UnknownErrorPage from "./UnknownErrorPage";

interface MyLoadingComponentProps {
    isLoading: boolean;
    error: Error | null;
}

const MyLoadingComponent: React.FC<MyLoadingComponentProps> = ({ isLoading, error }) => {
    if (isLoading) {
        return <div />;
    } else if (error) {
        console.info(error);
        return <UnknownErrorPage message={error.toString()} />;
    } else {
        return null;
    }
};

export default MyLoadingComponent;