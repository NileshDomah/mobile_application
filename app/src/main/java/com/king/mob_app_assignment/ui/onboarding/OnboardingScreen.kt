package com.king.mob_app_assignment.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import kotlinx.coroutines.launch
import com.king.mob_app_assignment.ui.theme.translatedStringResource

private data class OnboardingPage(
    val titleRes: Int,
    val bodyRes: Int,
    val isLogoPage: Boolean = false,
    val icon: ImageVector? = null,
)

private val pages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_page1_title,
        bodyRes = R.string.onboarding_page1_body,
        isLogoPage = true,
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page2_title,
        bodyRes = R.string.onboarding_page2_body,
        icon = Icons.Filled.Vibration,
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page3_title,
        bodyRes = R.string.onboarding_page3_body,
        icon = Icons.Filled.TrackChanges,
    ),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp)) {
            TextButton(
                onClick = onFinished,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Text(translatedStringResource(R.string.onboarding_skip))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { pageIndex ->
            OnboardingPageContent(pages[pageIndex])
        }

        PageIndicator(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )

        val isLastPage = pagerState.currentPage == pages.lastIndex
        PrimaryActionButton(
            text = if (isLastPage) {
                translatedStringResource(R.string.onboarding_get_started)
            } else {
                translatedStringResource(R.string.onboarding_next)
            },
            onClick = {
                if (isLastPage) {
                    onFinished()
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (page.isLogoPage) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
        } else if (page.icon != null) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(120.dp),
            )
        }

        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row {
            repeat(pageCount) { index ->
                val isSelected = index == currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 10.dp else 8.dp)
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
