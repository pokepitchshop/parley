terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  cloud {
    organization = "pokepitchshop-org"
    workspaces {
      name = "parley-platform"
    }
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}
