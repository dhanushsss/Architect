"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.ImpactLensProvider = void 0;
const vscode = __importStar(require("vscode"));
const PATH_MAP_TTL_MS = 5 * 60 * 1000;
let pathToEndpointId = new Map();
let mapLoadedAt = 0;
async function ensurePathMap(client) {
    const now = Date.now();
    if (now - mapLoadedAt < PATH_MAP_TTL_MS && pathToEndpointId.size > 0) {
        return;
    }
    const graph = await client.getGraph();
    const next = new Map();
    for (const n of graph.nodes ?? []) {
        if (n.type !== 'API_ENDPOINT' || !n.id?.startsWith('ep-'))
            continue;
        const id = n.id.replace(/^ep-/, '');
        const label = n.label ?? '';
        const m = label.match(/^(GET|POST|PUT|DELETE|PATCH)\s+(.+)$/i);
        if (!m)
            continue;
        const method = m[1].toUpperCase();
        const pathPart = normalizePathKey(m[2]);
        next.set(`${method} ${pathPart}`, id);
    }
    pathToEndpointId = next;
    mapLoadedAt = now;
}
function normalizePathKey(p) {
    return p.split('?')[0].replace(/\/+$/, '').toLowerCase() || '/';
}
function extractJavaRoutes(text) {
    const out = [];
    const lines = text.split(/\r?\n/);
    lines.forEach((line, idx) => {
        const vm = line.match(/@(?:Get|Post|Put|Delete|Patch)Mapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/i);
        if (vm) {
            let method = 'GET';
            if (/@PostMapping/i.test(line))
                method = 'POST';
            else if (/@PutMapping/i.test(line))
                method = 'PUT';
            else if (/@DeleteMapping/i.test(line))
                method = 'DELETE';
            else if (/@PatchMapping/i.test(line))
                method = 'PATCH';
            out.push({ line: idx, method, path: vm[1] });
            return;
        }
        const rm = line.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/i);
        if (rm && /RequestMapping/i.test(line)) {
            let method = 'GET';
            const mm = line.match(/method\s*=\s*RequestMethod\.(GET|POST|PUT|DELETE|PATCH)/i);
            if (mm)
                method = mm[1].toUpperCase();
            out.push({ line: idx, method, path: rm[1] });
        }
    });
    return out;
}
function extractJsRoutes(text) {
    const out = [];
    const lines = text.split(/\r?\n/);
    lines.forEach((line, idx) => {
        const m = line.match(/(?:app|router)\s*\.\s*(get|post|put|delete|patch)\s*\(\s*['"]([^'"]+)['"]/i);
        if (m) {
            out.push({ line: idx, method: m[1].toUpperCase(), path: m[2] });
        }
    });
    return out;
}
class ImpactLensProvider {
    getClient;
    dashboardUrl;
    constructor(getClient, dashboardUrl) {
        this.getClient = getClient;
        this.dashboardUrl = dashboardUrl;
    }
    getDashboardUrl() {
        return this.dashboardUrl;
    }
    async provideCodeLenses(document, _token) {
        const c = this.getClient();
        if (!c)
            return [];
        const lang = document.languageId;
        const text = document.getText();
        const routes = lang === 'java' ? extractJavaRoutes(text) : extractJsRoutes(text);
        if (routes.length === 0)
            return [];
        await ensurePathMap(c);
        const lenses = [];
        for (const r of routes) {
            const key = `${r.method} ${normalizePathKey(r.path)}`;
            const epId = pathToEndpointId.get(key);
            const line = document.lineAt(r.line);
            const range = new vscode.Range(r.line, 0, r.line, line.text.length);
            if (epId) {
                lenses.push(new vscode.CodeLens(range, {
                    title: 'Architect: checking impact…',
                    command: 'architect.showEndpointImpact',
                    arguments: [epId],
                }));
            }
            else {
                lenses.push(new vscode.CodeLens(range, {
                    title: 'Architect: route not in graph',
                    command: '',
                }));
            }
        }
        return lenses;
    }
}
exports.ImpactLensProvider = ImpactLensProvider;
