data "azurerm_client_config" "current" {}

# Foundation outputs (resource group + the app's managed identity).
data "terraform_remote_state" "foundation" {
  backend = "remote"
  config = {
    organization = "pokepitchshop-org"
    workspaces = {
      name = "parley-foundation"
    }
  }
}

locals {
  prefix       = "${var.project}-${var.environment}"
  rg_name      = data.terraform_remote_state.foundation.outputs.resource_group_name
  location     = data.terraform_remote_state.foundation.outputs.location
  principal_id = data.terraform_remote_state.foundation.outputs.identity_principal_id
}

# ---- Container Registry (image store for the app) ----
resource "azurerm_container_registry" "acr" {
  name                = "${var.project}${var.environment}acr" # globally unique, alphanumeric only
  resource_group_name = local.rg_name
  location            = local.location
  sku                 = "Basic"
  admin_enabled       = false
}

resource "azurerm_role_assignment" "acr_pull" {
  scope                = azurerm_container_registry.acr.id
  role_definition_name = "AcrPull"
  principal_id         = local.principal_id
}

# ---- Key Vault (RBAC mode) ----
resource "azurerm_key_vault" "kv" {
  name                      = "${local.prefix}-kv" # <= 24 chars, globally unique
  resource_group_name       = local.rg_name
  location                  = local.location
  tenant_id                 = data.azurerm_client_config.current.tenant_id
  sku_name                  = "standard"
  enable_rbac_authorization = true
}

resource "azurerm_role_assignment" "kv_secrets_user" {
  scope                = azurerm_key_vault.kv.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = local.principal_id
}

# ---- Azure OpenAI ----
resource "azurerm_cognitive_account" "openai" {
  name                  = "${local.prefix}-openai" # globally unique
  resource_group_name   = local.rg_name
  location              = local.location
  kind                  = "OpenAI"
  sku_name              = "S0"
  custom_subdomain_name = "${local.prefix}-openai" # required for token (keyless) auth
}

resource "azurerm_cognitive_deployment" "gpt4o_mini" {
  name                 = "gpt-4o-mini" # the deployment name Spring AI routes by
  cognitive_account_id = azurerm_cognitive_account.openai.id

  model {
    format  = "OpenAI"
    name    = "gpt-4o-mini"
    version = var.openai_model_version
  }

  sku {
    name     = "GlobalStandard"
    capacity = var.openai_capacity
  }
}

resource "azurerm_role_assignment" "openai_user" {
  scope                = azurerm_cognitive_account.openai.id
  role_definition_name = "Cognitive Services OpenAI User"
  principal_id         = local.principal_id
}

# ---- Container Apps environment ----
resource "azurerm_log_analytics_workspace" "logs" {
  name                = "${local.prefix}-logs"
  resource_group_name = local.rg_name
  location            = local.location
  sku                 = "PerGB2018"
  retention_in_days   = var.log_analytics_retention_days
  daily_quota_gb      = var.log_analytics_daily_quota_gb
}

resource "azurerm_container_app_environment" "env" {
  name                       = "${local.prefix}-cae"
  resource_group_name        = local.rg_name
  location                   = local.location
  log_analytics_workspace_id = azurerm_log_analytics_workspace.logs.id
}
