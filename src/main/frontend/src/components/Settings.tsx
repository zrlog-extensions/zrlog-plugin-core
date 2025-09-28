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
            await axios.post("api/setting/update", state);
            setShow(false);
        }} onCancel={() => setShow(false)}>
            <FormItem label={"评论插件"}>
                <Select onChange={(e) => {
                    setState({
                        commentPluginName: e,
                    })
                }} defaultValue={state.commentPluginName}>
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
            setState({...state, ...data});
            setShow(true);
        }}>{children}</Button>
    </>
}
export default Settings;