export interface Product {
  id: string
  sellerId: string
  name: string
  description: string
  priceAmount: number
  currency: string
  category: string
  active: boolean
  createdAt: string
}

export interface ProductPage {
  content: Product[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export interface LineItem {
  productId: string
  quantity: number
  unitPrice: number
  currency: string
}

export interface Cart {
  id: string
  userId: string
  items: LineItem[]
  updatedAt: string
}

export interface Order {
  id: string
  buyerId: string
  status: string
  totalAmount: number
  currency: string
  items: LineItem[]
  createdAt: string
  updatedAt: string
}

export interface ReportJob {
  executionId: number
  status: string
  reportDate: string
}
