package com.finance.user.service;

import com.finance.account.repository.AccountRepository;
import com.finance.auth.repository.RefreshTokenRepository;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.creditcard.repository.InvoicePaymentRepository;
import com.finance.goal.repository.GoalContributionRepository;
import com.finance.goal.repository.GoalRepository;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.transfer.repository.TransferRepository;
import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;
import com.finance.user.mapper.UserMapper;
import com.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoalContributionRepository goalContributionRepository;
    private final GoalRepository goalRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final TransactionRepository transactionRepository;
    private final TransferRepository transferRepository;
    private final CreditCardRepository creditCardRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    @Transactional
    public void delete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        goalContributionRepository.deleteAllByGoalUserId(userId);
        goalRepository.deleteAllByUserId(userId);
        invoicePaymentRepository.deleteAllByUserId(userId);
        transactionRepository.deleteAllByUserId(userId);
        transferRepository.deleteAllByUserId(userId);
        refreshTokenRepository.deleteAllByUserId(userId);
        creditCardRepository.deleteAllByUserId(userId);
        accountRepository.deleteAllByUserId(userId);
        categoryRepository.deleteAllByUserId(userId);
        userRepository.delete(user);
    }
}
