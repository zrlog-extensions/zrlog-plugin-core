/* eslint-disable */
import {createRoot} from "react-dom/client";
import * as serviceWorker from './serviceWorker';
import zh_CN from "antd/es/locale/zh_CN";
import {legacyLogicalPropertiesTransformer, StyleProvider} from "@ant-design/cssinjs";
import {useEffect, useState} from "react";
import {App, ConfigProvider, theme} from "antd";
import {BrowserRouter} from "react-router-dom";
import AppBase from "./AppBase";
import axios from "axios";

const {darkAlgorithm, defaultAlgorithm} = theme;

export interface PluginCoreInfoResponse {
    pluginBuildId: string
    pluginBuildNumber: string
    pluginVersion: string
    pluginCenter: string
    dark: boolean
    primaryColor: string
    plugins: Plugin[]
}

export interface Plugin {
    id: string
    version: string
    name: string
    paths: string[]
    actions: any[]
    desc: string
    author: string
    shortName: string
    indexPage: string
    previewImageBase64: string
    services: string[]
    dependentService: string[]
}

const loadFromDocument = () => {
    try {
        const a = document.getElementById("pluginInfo");
        if (a === null || a.innerText.length === 0) {
            return null;
        }
        return covertData(JSON.parse(a.innerText));
    } catch (e) {
        return null;
    }
}

const covertData = (data: PluginCoreInfoResponse) => {
    let locationHref = window.location.href;
    if (locationHref.endsWith("/")) {
        locationHref = locationHref.substring(0, locationHref.length - 1);
    }
    return {
        ...data,
        pluginCenter: data.pluginCenter.replace(`#locationHref`, locationHref)
    }
}

const Index = () => {
    const [pluginInfo, setPluginInfo] = useState<PluginCoreInfoResponse | null>(loadFromDocument);

    useEffect(() => {
        if (pluginInfo === null) {
            axios.get("api/plugins").then(({data}) => {
                setPluginInfo(covertData(data));
            });
        }
    }, []);

    if (pluginInfo === null) {
        return <></>
    }

    return (
        <ConfigProvider
            locale={zh_CN}
            theme={{
                algorithm: pluginInfo.dark ? darkAlgorithm : defaultAlgorithm,
                token: {
                    colorPrimary: pluginInfo.primaryColor
                }
            }}
            divider={{
                style: {
                    margin: "16px 0px"
                }
            }}
            table={
                {
                    style: {
                        whiteSpace: "nowrap"
                    },
                }}
        >
            <BrowserRouter>
                <StyleProvider transformers={[legacyLogicalPropertiesTransformer]}>
                    <App>
                        <AppBase pluginInfo={pluginInfo}/>
                    </App>
                </StyleProvider>
            </BrowserRouter>
        </ConfigProvider>
    );
};

const container = document.getElementById("app");
const root = createRoot(container!); // createRoot(container!) if you use TypeScript
root.render(<Index/>);
// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: http://bit.ly/CRA-PWA
serviceWorker.unregister();