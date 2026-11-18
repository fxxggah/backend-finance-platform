package com.finance.user.service;
import com.finance.billing.service.BillingDeletionService;
import com.finance.billing.service.ProAccessService;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;
import com.finance.user.mapper.UserMapper;
import com.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final java.time.Clock clock;
    private final ProAccessService proAccess;
    private final BillingDeletionService deletion;
    @Transactional(readOnly=true)
    public UserResponse get(Long userId) {
        var user = getActiveUser(userId);
        return UserMapper.toResponse(user, clock, proAccess.paidUntil(userId, clock.instant()));
    }
    @Transactional(readOnly=true)
    public User getActiveUser(Long userId) {
        return userRepository.findById(userId).filter(User::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void delete(Long userId) { deletion.delete(userId); }
}
