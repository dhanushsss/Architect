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
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const architectClient_1 = require("./architectClient");
const config_1 = require("./config");
const impactLensProvider_1 = require("./impactLensProvider");
let client = null;
function activate(context) {
    const refreshClient = () => {
        const jwt = (0, config_1.getJwtToken)();
        client = jwt ? new architectClient_1.ArchitectClient((0, config_1.getApiBase)(), jwt) : null;
    };
    refreshClient();
    const dashboardUrl = `${(0, config_1.getApiBase)().replace(/\/$/, '')}/graph`;
    const provider = new impactLensProvider_1.ImpactLensProvider(() => client, dashboardUrl);
    context.subscriptions.push(vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration('architect'))
            refreshClient();
    }));
    const sel = [
        { language: 'java', scheme: 'file' },
        { language: 'typescript', scheme: 'file' },
        { language: 'javascript', scheme: 'file' },
    ];
    context.subscriptions.push(vscode.languages.registerCodeLensProvider(sel, {
        provideCodeLenses: async (doc, token) => {
            if (!(0, config_1.isCodeLensEnabled)())
                return [];
            return provider.provideCodeLenses(doc, token);
        },
    }));
    context.subscriptions.push(vscode.commands.registerCommand('architect.showEndpointImpact', async (endpointId) => {
        if (!client) {
            vscode.window.showWarningMessage('Set architect.jwtToken in VS Code settings (copy JWT after login).');
            return;
        }
        try {
            const impact = await client.getEndpointImpact(endpointId);
            const n = impact.dependentsCount ?? 0;
            const label = impact.subjectLabel ?? 'endpoint';
            const pick = await vscode.window.showInformationMessage(`${n} repo(s) depend on ${label} — View in Architect`, 'Open Architect');
            if (pick === 'Open Architect') {
                vscode.env.openExternal(vscode.Uri.parse(provider.getDashboardUrl()));
            }
        }
        catch (e) {
            const msg = e instanceof Error ? e.message : String(e);
            vscode.window.showErrorMessage(`Architect: ${msg}`);
        }
    }));
}
function deactivate() {
    client = null;
}
