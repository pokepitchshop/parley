terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  # State + runs in HCP Terraform. Create this workspace in pokepitchshop first.
  # For staging/prod, point at parley-foundation-staging / -prod instead.
  cloud {
    organization = "pokepitchshop"
    workspaces {
      name = "parley-foundation"
    }
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}
