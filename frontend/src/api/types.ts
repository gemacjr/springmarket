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
