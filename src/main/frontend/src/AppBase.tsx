import {Route, Routes} from "react-router";
import CoreIndex from "./components/CoreIndex";
import DownloadResult from "./components/DownloadResult";
import PluginStarted from "./components/PluginStarted";
import {PluginCoreInfoResponse} from "./index";
import {FunctionComponent} from "react";

export type AppBaseProps = {
    pluginInfo: PluginCoreInfoResponse;
}

const AppBase: FunctionComponent<AppBaseProps> = ({pluginInfo}) => {

    return (
        <Routes>
            <Route path='/admin/plugins/downloadResult' element={<DownloadResult/>}/>
            <Route path='/downloadResult' element={<DownloadResult/>}/>
            <Route path='/admin/plugins/pluginStarted' element={<PluginStarted/>}/>
            <Route path='/pluginStarted' element={<PluginStarted/>}/>
            <Route path="*" element={<CoreIndex data={pluginInfo}/>}/>
        </Routes>
    );
}

export default AppBase;
