data "terraform_remote_state" "foundation" {
  backend = "remote"
  config = {
    organization = "pokepitchshop"
    workspaces = {
      name = "parley-foundation"
    }
  }
}

data "terraform_remote_state" "platform" {
  backend = "remote"
  config = {
    organization = "pokepitchshop"
    workspaces = {
      name = "parley-platform"
    }
  }
}

locals {
  prefix             = "${var.project}-${var.environment}"
  identity_id        = data.terraform_remote_state.foundation.outputs.identity_id
  identity_client_id = data.terraform_remote_state.foundation.outputs.identity_client_id
  rg_name            = data.terraform_remote_state.platform.outputs.resource_group_name
  env_id             = data.terraform_remote_state.platform.outputs.container_app_environment_id
  acr_server         = data.terraform_remote_state.platform.outputs.acr_login_server
  openai_endpoint    = data.terraform_remote_state.platform.outputs.openai_endpoint
  openai_deploy      = data.terraform_remote_state.platform.outputs.openai_deployment_name
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
    name                = "twilio-auth-token"
    key_vault_secret_id = var.twilio_token_secret_id
    identity            = local.identity_id
  }

  secret {
    name                = "mongodb-uri"
    key_vault_secret_id = var.mongodb_uri_secret_id
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
    min_replicas = var.min_replicas
    max_replicas = var.max_replicas

    container {
      name   = "parley"
      image  = "${local.acr_server}/${var.project}:${var.image_tag}"
      cpu    = 0.5
      memory = "1Gi"

      # Azure OpenAI: keyless via user-assigned managed identity (platform RBAC grant).
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "azure"
      }
      env {
        name  = "AZURE_CLIENT_ID"
        value = local.identity_client_id
      }
      env {
        name  = "SPRING_AI_OPENAI_BASE_URL"
        value = local.openai_endpoint
      }
      env {
        name  = "SPRING_AI_OPENAI_MICROSOFT_FOUNDRY"
        value = "true"
      }
      env {
        name  = "SPRING_AI_OPENAI_CHAT_MICROSOFT_DEPLOYMENT_NAME"
        value = local.openai_deploy
      }
      env {
        name  = "SPRING_AI_OPENAI_CHAT_MODEL"
        value = local.openai_deploy
      }
      env {
        name  = "TWILIO_ACCOUNT_SID"
        value = var.twilio_account_sid
      }
      env {
        name        = "TWILIO_AUTH_TOKEN"
        secret_name = "twilio-auth-token"
      }
      env {
        name        = "SPRING_DATA_MONGODB_URI"
        secret_name = "mongodb-uri"
      }

      liveness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/health"
        interval_seconds        = 10
        failure_count_threshold = 3
      }

      readiness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/health"
        interval_seconds        = 5
        success_count_threshold = 1
      }
    }
  }
}
