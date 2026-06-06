package com.ico.nekofeed.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.repository.FeedRepository

class FeedPagingSource(
    private val repository: FeedRepository,
    private val category: FeedCategory? = null
) : PagingSource<Int, FeedItem>() {

    override fun getRefreshKey(state: PagingState<Int, FeedItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedItem> {
        val page = params.key ?: 0
        val pageSize = params.loadSize

        return try {
            val categoryParam = if (category == FeedCategory.FEATURED) null else category?.value
            val result = repository.loadFeed(
                category = categoryParam,
                limit = pageSize,
                offset = page * pageSize
            )

            result.fold(
                onSuccess = { items ->
                    LoadResult.Page(
                        data = items,
                        prevKey = if (page == 0) null else page - 1,
                        nextKey = if (items.isEmpty()) null else page + 1
                    )
                },
                onFailure = { error ->
                    LoadResult.Error(error)
                }
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
