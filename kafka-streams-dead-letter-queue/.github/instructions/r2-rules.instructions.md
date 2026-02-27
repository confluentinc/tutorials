# Agent Rules

Generated on: 2026-02-27 15:15:49
Total rules: 5 files

All links in this rule file are relative to the rules directory `<workspace path>/.github/instructions/`. Therefore `../../.r2-rules/xyz.md` should be resolved to `<workspace path>/.r2-rules/xyz.md`. For each workspace path in the user's environment, resolve the relative path from that workspace root.
---

## recommended-frameworks

# Recommended Frameworks

## Go Frameworks

Name | Purpose
--- | ---
[service-runtime-go](../../.r2-rules/srg-first.md) | Core framework for building microservices in golang following Confluent standards and best practices


---

## global-rules

# Global Agent Rules

Company-wide rules for AI coding assistants at Confluent.

## Security

**Follow all security controls.** Only bypass with explicit user request, rationale, and confirmation.

Airlock prevents proprietary code from being pushed to public repositories. For external pushes, use `git push-external` (standard `git push` will fail). See `man git-push-external` for details.

## Recommended Tools

- [Recommended Tools](#recommended-tools)

## Recommended Frameworks

- [Recommended Frameworks](#recommended-frameworks)


---

## recommended-tools

# Recommended Tools

## Internal CLI Tools

- One-time: `brew tap confluentinc/internal`
- Install: `brew update && brew install <tool-name>`

Name | Purpose
--- | ---
[r2](#r2-cli) | AI coding assistant toolkit (rules, history, tools)
chewie | AI Ops for infrastructure/K8s/monitoring
k8saas | K8SaaS cluster management
trafficctl | Manage Confluent Cloud Traffic resources
rok | Resource Manager CLI (kubectl for RM)

## Third-party CLI Tools

Name | Purpose | Install
--- | --- | ---
[gh](../../.r2-rules/github-cli.md) | GitHub CLI for PRs, issues, repos | `brew install gh`
[sem](../../.r2-rules/semaphore-cli.md) | SemaphoreCI pipeline management | `brew install semaphoreci/tap/sem`

## MCP Servers

Installing stdio servers:
- Claude Code: `claude mcp add <name> -s user -- <command>`
- Cursor: `jq '.mcpServers.<name> = {command: "<cmd>", args: ["<arg1>", "<arg2>"]}' ~/.cursor/mcp.json > /tmp/mcp.json && mv /tmp/mcp.json ~/.cursor/mcp.json` (use `[]` if no args)

Name | Purpose | Command
--- | --- | ---
chewie | AI Ops tools | chewie mcp-server serve
context7 | Library docs and examples | npx -y @upstash/context7-mcp@latest

For Cursor, install chewie with `chewie cursor configure`

Installing remote servers (Claude Code only):
- `claude mcp add <name> <url> --transport sse`
- After adding, authenticate from inside a Claude Code session by running `/mcp` and selecting the server

Name | Purpose | URL
--- | --- | ---
atlassian | Confluence and Jira access | https://mcp.atlassian.com/v1/sse

**Note:** Enabled MCP tools consume context window tokens. Adjust enabled tools based on your task.
- Cursor: Use "Open MCP Settings" command to toggle individual tools
- Claude Code: `claude mcp remove <name>` to disable entire server (no per-tool control yet)


---

## r2-cli

# R2 CLI

Developer toolset for AI coding assistants. Use `r2 --help` for full capabilities.

**Core features:**
- [AI coding rules management](#managing-rules)
- IDE extensions (automatic background pull)
- [Query conversation history](../../.r2-rules/querying-claude-history.md)


---

## managing-rules

# Managing R2 Rules

When creating, updating, or organizing R2 rules, follow the structured process in the [Managing Rules Guide](../../.r2-rules/managing-rules-guide.md).

**Key points:**

- **Default to personal rules** unless user specifies broader scope (team/common)
- **Check user's setup first** - read `~/.config/r2-rules/user-rules.md` to determine if they use local-only or r2-rules repo
- **Read the full guide before starting** - it covers branch setup, file placement, index updates, and testing

The guide walks through:
1. Discovering current setup
2. Locating/setting up the repository
3. Creating or updating rule files
4. Updating index files with proper markers
5. Committing, pushing, and testing

