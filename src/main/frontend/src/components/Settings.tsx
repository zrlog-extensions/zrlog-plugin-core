import {Button, Form, Modal, Switch, message} from "antd";
import {CSSProperties, FunctionComponent, PropsWithChildren, useEffect, useState} from "react";
import axios from "axios";
import {PluginCoreSetting} from "../index";

type SettingsStatus = {
    autoDownloadLostFile: boolean;
}

type SettingsProps = {
    style: CSSProperties;
    setting?: PluginCoreSetting;
}

const stateFromSetting = (setting?: PluginCoreSetting): SettingsStatus => ({
    autoDownloadLostFile: setting?.disableAutoDownloadLostFile !== true
});

const Settings: FunctionComponent<PropsWithChildren<SettingsProps>> = ({children, style, setting}) => {

    const [show, setShow] = useState(false);
    const [messageApi, contextHolder] = message.useMessage({maxCount: 3});

    const [savedState, setSavedState] = useState<SettingsStatus>(stateFromSetting(setting))
    const [state, setState] = useState<SettingsStatus>(stateFromSetting(setting))

    useEffect(() => {
        const nextState = stateFromSetting(setting);
        setSavedState(nextState);
        setState(nextState);
    }, [setting?.disableAutoDownloadLostFile]);

    const getBody = () => {
        return <Modal title={"插件设置"} open={show} onOk={async () => {
            const params = new URLSearchParams();
            params.set("disableAutoDownloadLostFile", String(!state.autoDownloadLostFile));
            const {data} = await axios.post("api/setting/update", params.toString());
            if (data.code > 0) {
                messageApi.error(data.message);
                return;
            }
            setSavedState(state);
            setShow(false);
        }} onCancel={() => {
            setState(savedState);
            setShow(false);
        }}>
            <Form.Item label={"自动补齐必要插件"} style={{marginBottom: 0}}>
                <Switch checked={state.autoDownloadLostFile} onChange={(checked) => {
                    setState({
                        autoDownloadLostFile: checked,
                    })
                }}/>
            </Form.Item>
        </Modal>
    }

    return <>
        {contextHolder}
        {getBody()}
        <Button style={style} onClick={() => {
            setShow(true);
        }}>{children}</Button>
    </>
}
export default Settings;
