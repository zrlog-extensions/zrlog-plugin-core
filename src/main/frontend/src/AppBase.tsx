import {Route, Routes} from "react-router";
import CoreIndex from "./components/CoreIndex";
import DownloadResult from "./components/DownloadResult";
import PluginStarted from "./components/PluginStarted";
import {PluginCoreInfoResponse} from "./index";
import {FunctionComponent} from "react";
import RuntimeCenter from "./components/RuntimeCenter";

export type AppBaseProps = {
    pluginInfo: PluginCoreInfoResponse;
    onPluginInfoRefresh: () => Promise<void>;
}

const AppBase: FunctionComponent<AppBaseProps> = ({pluginInfo, onPluginInfoRefresh}) => {

    const runtimeCenter = <RuntimeCenter data={pluginInfo}/>;

    return (
        <Routes>
            <Route path='/admin/plugins/downloadResult' element={<DownloadResult/>}/>
            <Route path='/downloadResult' element={<DownloadResult/>}/>
            <Route path='/admin/plugins/pluginStarted' element={<PluginStarted/>}/>
            <Route path='/pluginStarted' element={<PluginStarted/>}/>
            <Route path='/admin/plugins/runtime-scheduler/*' element={runtimeCenter}/>
            <Route path='/p/runtime-scheduler/*' element={runtimeCenter}/>
            <Route path='/plugin/runtime-scheduler/*' element={runtimeCenter}/>
            <Route path='/runtime-scheduler/*' element={runtimeCenter}/>
            <Route path='/admin/plugins/runtime-states/*' element={runtimeCenter}/>
            <Route path='/p/runtime-states/*' element={runtimeCenter}/>
            <Route path='/plugin/runtime-states/*' element={runtimeCenter}/>
            <Route path='/runtime-states/*' element={runtimeCenter}/>
            <Route path='/admin/plugins/runtime-notification/*' element={runtimeCenter}/>
            <Route path='/p/runtime-notification/*' element={runtimeCenter}/>
            <Route path='/plugin/runtime-notification/*' element={runtimeCenter}/>
            <Route path='/runtime-notification/*' element={runtimeCenter}/>
            <Route path='/admin/plugins/runtime-services/*' element={runtimeCenter}/>
            <Route path='/p/runtime-services/*' element={runtimeCenter}/>
            <Route path='/plugin/runtime-services/*' element={runtimeCenter}/>
            <Route path='/runtime-services/*' element={runtimeCenter}/>
            <Route path="*" element={<CoreIndex data={pluginInfo} onRefresh={onPluginInfoRefresh}/>}/>
        </Routes>
    );
}

export default AppBase;
