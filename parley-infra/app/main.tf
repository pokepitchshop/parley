data "terraform_remote_state" "foundation" {
  backend = "remote"
  config = {
    organization = "pokepitchshop-org"
    workspaces = {
      name = "parley-foundation"
    }
  }
}

data "terraform_remote_state" "platform" {
  backend = "remote"
  config = {
    organization = "pokepitchshop-org"
    workspaces = {
      name = "parley-platform"
    }
  }
}

locals {
  prefix          = "${var.project}-${var.environment}"
  identity_id     = data.terraform_remote_state.foundation.outputs.identity_id
  rg_name         = data.terraform_remote_state.platform.outputs.resource_group_name
  env_id          = data.terraform_remote_state.platform.outputs.container_app_environment_id
  acr_server      = data.terraform_remote_state.platform.outputs.acr_login_server
  openai_endpoint = data.terraform_remote_state.platform.outputs.openai_endpoint
  openai_deploy   = data.terraform_remote_state.platform.outputs.openai_deployment_name
}

resource "azurerm_container_app" "parley" {
  name                         = "${local.prefix}-app"
  resource_group_name          = local.rg_name
  container_app_environment_id = local.env_id
  revision_mode                = "Single"

  identity {
    type         = "UserAssigned"
    identity_ids = [local.identity_id]
  }

  # Pull the image from ACR using the managed identity (AcrPull granted in platform).
  registry {
    server   = local.acr_server
    identity = local.identity_id
  }

  # Secrets pulled from Key Vault via the managed identity.
  secret {
    name                = "azure-openai-key"
    key_vault_secret_id = var.openai_key_secret_id
    identity            = local.identity_id
  }

  secret {
    name                = "twilio-auth-token"
    key_vault_secret_id = var.twilio_token_secret_id
    identity            = local.identity_id
  }

  # Public HTTPS endpoint Twilio posts to.
  ingress {
    external_enabled = true
    target_port      = 8080
    transport        = "auto"

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = 1
    max_replicas = 2

    container {
      name   = "parley"
      image  = "${local.acr_server}/${var.project}:${var.image_tag}"
      cpu    = 0.5
      memory = "1Gi"

      # Spring relaxed binding: SPRING_AI_AZURE_OPENAI_ENDPOINT -> spring.ai.azure.openai.endpoint
      env {
        name  = "SPRING_AI_AZURE_OPENAI_ENDPOINT"
        value = local.openai_endpoint
      }
      env {
        name  = "SPRING_AI_AZURE_OPENAI_CHAT_OPTIONS_DEPLOYMENT_NAME"
        value = local.openai_deploy
      }
      env {
        name        = "SPRING_AI_AZURE_OPENAI_API_KEY"
        secret_name = "azure-openai-key"
      }
      env {
        name  = "TWILIO_ACCOUNT_SID"
        value = var.twilio_account_sid
      }
      env {
        name        = "TWILIO_AUTH_TOKEN"
        secret_name = "twilio-auth-token"
      }
    }
  }
}
