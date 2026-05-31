const absoluteApiBase = () => {
    const pathname = window.location.pathname;
    if (pathname.startsWith("/admin/plugins")) {
        return "/admin/plugins/api";
    }
    if (pathname.startsWith("/p/") || pathname === "/p") {
        return "/p/api";
    }
    if (pathname.startsWith("/plugin/") || pathname === "/plugin") {
        return "/plugin/api";
    }
    return "/api";
};

const currentDirectorySegments = () => {
    const pathname = window.location.pathname || "/";
    const directory = pathname.endsWith("/")
        ? pathname
        : pathname.substring(0, pathname.lastIndexOf("/") + 1);
    return directory.split("/").filter(Boolean);
};

const relativePath = (absolutePath: string) => {
    const from = currentDirectorySegments();
    const to = absolutePath.split("/").filter(Boolean);
    let shared = 0;
    while (shared < from.length && shared < to.length && from[shared] === to[shared]) {
        shared += 1;
    }
    const up = new Array(from.length - shared).fill("..");
    const down = to.slice(shared);
    return up.concat(down).join("/") || ".";
};

export const apiBase = () => relativePath(absoluteApiBase());

export const apiPath = (path: string) => {
    const normalizedPath = path.replace(/^\/+/, "");
    return `${apiBase()}/${normalizedPath}`;
};
