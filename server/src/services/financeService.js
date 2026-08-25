const financeRepository = require('../repositories/financeRepository');

class FinanceService {
  async getAccounts(userId) {
    return financeRepository.getAccounts(userId);
  }

  async createAccount(userId, data) {
    const { accountName, bankName, accountNumberMasked, accountType, openingBalance, isDefault } = data;
    const now = Date.now();
    const result = financeRepository.insertAccount(
      userId,
      accountName || 'New Account',
      bankName || '',
      accountNumberMasked || '',
      accountType || 'BANK',
      openingBalance || 0.0,
      isDefault,
      now
    );
    return financeRepository.getAccountById(result.lastInsertRowid, userId);
  }

  async getCategories(userId) {
    return financeRepository.getCategories(userId);
  }

  async createCategory(userId, data) {
    const { name, type, icon, color } = data;
    const now = Date.now();
    const result = financeRepository.insertCategory(userId, name, type || 'EXPENSE', icon || 'category', color || '#64748B', now);
    return financeRepository.getCategoryById(result.lastInsertRowid, userId);
  }

  async getRules(userId) {
    return financeRepository.getRules(userId);
  }

  async createRule(userId, data) {
    const { pattern, categoryId, categoryName, transactionType, priority, matchType } = data;
    const now = Date.now();
    const result = financeRepository.insertRule(
      userId,
      pattern,
      categoryId || null,
      categoryName,
      transactionType || 'EXPENSE',
      priority || 10,
      matchType || 'CONTAINS',
      now
    );
    return financeRepository.getRuleById(result.lastInsertRowid, userId);
  }

  async getTransactions(userId) {
    return financeRepository.getTransactions(userId);
  }

  async getTransactionById(txId, userId) {
    return financeRepository.getTransactionById(txId, userId);
  }

  async createTransaction(userId, data) {
    const { accountId, transactionDate, description, debitAmount, creditAmount, amount, transactionType, categoryId, categoryName, notes, source } = data;
    const now = Date.now();

    // Validate account belongs to user
    let account = accountId ? financeRepository.getAccountById(accountId, userId) : null;
    if (!account) {
      const userAccounts = financeRepository.getAccounts(userId);
      if (userAccounts.length > 0) {
        account = userAccounts[0];
      } else {
        account = await this.createAccount(userId, { accountName: 'Cash', bankName: 'Cash', accountType: 'CASH', openingBalance: 0.0, isDefault: 1 });
      }
    }
    const targetAccountId = account.id;


    const result = financeRepository.insertTransaction(
      userId,
      targetAccountId,
      transactionDate || new Date().toISOString().split('T')[0],
      description || 'New Transaction',
      debitAmount || 0,
      creditAmount || 0,
      amount || 0,
      transactionType || 'EXPENSE',
      categoryId || null,
      categoryName || 'Uncategorized',
      notes || '',
      source || 'MANUAL',
      now
    );

    return financeRepository.getTransactionById(result.lastInsertRowid, userId);
  }

  async getLoans(userId) {
    return financeRepository.getLoans(userId);
  }

  async createLoan(userId, data) {
    const { borrowerLenderName, loanType, amount, interestRate, dueDate, notes } = data;
    const now = Date.now();
    const result = financeRepository.insertLoan(
      userId,
      borrowerLenderName,
      loanType || 'GIVEN',
      amount,
      interestRate || 0.0,
      dueDate || '',
      notes || '',
      now
    );
    return financeRepository.getLoanById(result.lastInsertRowid, userId);
  }

  async recordRepayment(userId, loanId, data) {
    const { amount, repaymentDate, paymentMethod, notes } = data;
    const now = Date.now();

    const loan = financeRepository.getLoanById(loanId, userId);
    if (!loan) {
      throw new Error('LOAN_NOT_FOUND');
    }

    const result = financeRepository.insertRepayment(
      userId,
      loanId,
      amount,
      repaymentDate || new Date().toISOString().split('T')[0],
      paymentMethod || 'CASH',
      notes || '',
      now
    );

    const totalRepaidRes = financeRepository.getRepaymentsSum(loanId, userId);
    const totalRepaid = totalRepaidRes.total || 0;
    const remaining = Math.max(0, loan.amount - totalRepaid);
    const status = remaining <= 0 ? 'PAID' : totalRepaid > 0 ? 'PARTIALLY_PAID' : 'ACTIVE';

    financeRepository.updateLoanStatus(loanId, userId, totalRepaid, remaining, status, now);

    return { repaymentId: result.lastInsertRowid, totalRepaid, remaining, status };
  }

  async getCompanyExpenses(userId) {
    return financeRepository.getCompanyExpenses(userId);
  }

  async createCompanyExpense(userId, data) {
    const { expenseDate, title, amount, categoryName, notes } = data;
    const now = Date.now();
    const result = financeRepository.insertCompanyExpense(
      userId,
      expenseDate || new Date().toISOString().split('T')[0],
      title,
      amount,
      categoryName || 'Official',
      notes || '',
      now
    );
    return financeRepository.getCompanyExpenses(userId).find(e => e.id === result.lastInsertRowid);
  }

  async getDashboardData(userId) {
    const accounts = financeRepository.getAccounts(userId);
    const totalBalance = accounts.reduce((acc, a) => acc + (a.current_balance || 0), 0);
    const txs = financeRepository.getRecentTransactions(userId, 10);
    const loans = financeRepository.getActiveLoans(userId);
    const outstandingLent = loans.filter(l => l.loan_type === 'GIVEN').reduce((acc, l) => acc + l.remaining_amount, 0);

    return {
      totalBalance,
      accountsCount: accounts.length,
      recentTransactions: txs,
      outstandingLent,
      activeLoansCount: loans.length
    };
  }
}

module.exports = new FinanceService();
