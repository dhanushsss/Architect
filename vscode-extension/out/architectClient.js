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
exports.ArchitectClient = void 0;
const https = __importStar(require("https"));
const http = __importStar(require("http"));
class ArchitectClient {
    baseUrl;
    jwt;
    constructor(baseUrl, jwt) {
        this.baseUrl = baseUrl;
        this.jwt = jwt;
    }
    async getGraph() {
        return this.requestJson('GET', '/api/v1/graph');
    }
    async getEndpointImpact(endpointId) {
        return this.requestJson('GET', `/api/v1/impact/endpoint/${encodeURIComponent(endpointId)}`);
    }
    requestJson(method, path) {
        const base = this.baseUrl.endsWith('/') ? this.baseUrl.slice(0, -1) : this.baseUrl;
        const url = new URL(path.startsWith('/') ? path : `/${path}`, `${base}/`);
        const isHttps = url.protocol === 'https:';
        const lib = isHttps ? https : http;
        const headers = {
            Accept: 'application/json',
        };
        if (this.jwt) {
            headers['Authorization'] = `Bearer ${this.jwt}`;
        }
        return new Promise((resolve, reject) => {
            const req = lib.request({
                hostname: url.hostname,
                port: url.port || (isHttps ? 443 : 80),
                path: url.pathname + url.search,
                method,
                headers,
            }, (res) => {
                const chunks = [];
                res.on('data', (c) => chunks.push(c));
                res.on('end', () => {
                    const body = Buffer.concat(chunks).toString('utf8');
                    if (res.statusCode && res.statusCode >= 400) {
                        reject(new Error(`Architect ${res.statusCode}: ${body}`));
                        return;
                    }
                    try {
                        resolve(JSON.parse(body));
                    }
                    catch (e) {
                        reject(e);
                    }
                });
            });
            req.on('error', reject);
            req.end();
        });
    }
}
exports.ArchitectClient = ArchitectClient;
