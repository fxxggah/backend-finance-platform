# Lista de Endpoints da API (v1)

## 2. Auth
- `POST /auth/google`
- `POST /auth/refresh`
- `POST /auth/logout`

## 3. Usuário
- `GET /users/me`
- `DELETE /users/me`

## 4. Contas
- `GET /accounts`
- `POST /accounts`
- `PUT /accounts/{id}`
- `DELETE /accounts/{id}`

## 5. Categorias
- `GET /categories`
- `POST /categories`
- `PUT /categories/{id}`
- `DELETE /categories/{id}`

## 6. Transações
- `GET /transactions`
- `GET /transactions/{id}`
- `POST /transactions`
- `PUT /transactions/{id}`
- `DELETE /transactions/{id}`

## 7. Transferências
- `POST /transfers`
- `GET /transfers`
- `GET /transfers/{id}`
- `PUT /transfers/{id}`
- `DELETE /transfers/{id}`

## 8. Cartões de crédito
- `GET /credit-cards`
- `POST /credit-cards`
- `PUT /credit-cards/{id}`
- `DELETE /credit-cards/{id}`
- `GET /credit-cards/{id}/invoices`
- `GET /credit-cards/{id}/invoices/{yyyy-MM}`
- `POST /credit-cards/{id}/invoices/{yyyy-MM}/payments`
- `DELETE /credit-cards/{id}/invoices/{yyyy-MM}/payments/{paymentId}`

## 9. Dashboard
- `GET /dashboard/summary`
- `GET /dashboard/expenses-by-category`
- `GET /dashboard/evolution`
- `GET /dashboard/recent-transactions`

## 10. Metas
- `GET /goals`
- `GET /goals/{id}`
- `POST /goals`
- `PUT /goals/{id}`
- `PATCH /goals/{id}/cancel`
- `DELETE /goals/{id}`
- `GET /goals/{id}/contributions`
- `POST /goals/{id}/contributions`
- `DELETE /goals/{id}/contributions/{contributionId}`

## 11. Simulações
- `POST /simulations/investment`
- `POST /simulations/debt-payoff`

## 12. Infraestrutura / Públicos
- `GET /actuator/health`
- `GET /v3/api-docs/**`
- `GET /swagger-ui/**`
