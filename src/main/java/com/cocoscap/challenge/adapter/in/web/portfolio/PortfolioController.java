package com.cocoscap.challenge.adapter.in.web.portfolio;

import com.cocoscap.challenge.application.portfolio.GetPortfolioUseCase;
import com.cocoscap.challenge.domain.user.UserId;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{userId}/portfolio")
@Validated
public class PortfolioController {

    private final GetPortfolioUseCase getPortfolioUseCase;

    public PortfolioController(GetPortfolioUseCase getPortfolioUseCase) {
        this.getPortfolioUseCase = getPortfolioUseCase;
    }

    @GetMapping
    PortfolioResponse get(@PathVariable @Min(1) long userId) {
        return PortfolioResponse.from(getPortfolioUseCase.execute(UserId.of(userId)));
    }
}
