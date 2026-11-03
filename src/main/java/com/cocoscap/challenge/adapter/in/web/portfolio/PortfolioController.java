package com.cocoscap.challenge.adapter.in.web.portfolio;

import com.cocoscap.challenge.application.portfolio.GetPortfolioUseCase;
import com.cocoscap.challenge.domain.user.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{userId}/portfolio")
@Tag(name = "Portfolio")
public class PortfolioController {

    private final GetPortfolioUseCase getPortfolioUseCase;

    public PortfolioController(GetPortfolioUseCase getPortfolioUseCase) {
        this.getPortfolioUseCase = getPortfolioUseCase;
    }

    @GetMapping
    @Operation(
            summary = "Get user portfolio",
            description =
                    "Percentage fields (*_pct) are in percentage points; missing market data is explicit null.")
    @ApiResponse(responseCode = "200", description = "Portfolio for the user")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    PortfolioResponse get(@PathVariable @Min(1) long userId) {
        return PortfolioResponse.from(getPortfolioUseCase.execute(UserId.of(userId)));
    }
}
