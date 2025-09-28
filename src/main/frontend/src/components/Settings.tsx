import {Button, Modal, Select} from "antd";
import FormItem from "antd/es/form/FormItem";
import {Option} from "rc-select";
import {CSSProperties, FunctionComponent, PropsWithChildren, useState} from "react";
import axios from "axios";

type SettingsStatus = {
    commentPluginName: string;
}

type SettingsProps = {
    style: CSSProperties;
}

const Settings: FunctionComponent<PropsWithChildren<SettingsProps>> = ({children, style}) => {

    const [show, setShow] = useState(false);

    const [state, setState] = useState<SettingsStatus>({
        commentPluginName: "comment"
    })

    const getBody = () => {
        return <Modal title={"设置"} open={show} onOk={async () => {
            const params = new URLSearchParams();
            params.set("commentPluginName", state.commentPluginName);
            await axios.post("api/setting/update", params.toString());
            setShow(false);
        }} onCancel={() => setShow(false)}>
            <FormItem label={"评论插件"}>
                <Select onChange={(e) => {
                    setState({
                        commentPluginName: e,
                    })
                }} defaultValue={state.commentPluginName} style={{maxWidth: 156}}>
                    <Option value={"comment"}>默认</Option>
                    <Option value={"changyan"}>畅言</Option>
                </Select>
            </FormItem>
        </Modal>
    }

    return <>
        {getBody()}
        <Button style={style} onClick={async () => {
            const {data} = await axios.get("api/setting/load")
            setState(data);
            setShow(true);
        }}>{children}</Button>
    </>
}
export default Settings;