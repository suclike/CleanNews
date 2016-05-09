package clean.news.data.retrofit

import clean.news.app.data.item.ItemNetworkDataSource
import clean.news.core.entity.Item
import clean.news.core.entity.Item.ListType
import clean.news.data.retrofit.service.ItemService
import rx.Observable
import javax.inject.Inject

class ItemRetrofitDataSource @Inject constructor(
		private val itemService: ItemService) : ItemNetworkDataSource {

	private val BUFFER = 5

	override fun getItems(listType: ListType): Observable<List<Item>> {
		val items = when (listType) {
			Item.ListType.TOP -> itemService.getTopStories()
			Item.ListType.NEW -> itemService.getNewStories()
			Item.ListType.SHOW -> itemService.getShowStories()
			Item.ListType.ASK -> itemService.getAskStories()
			Item.ListType.JOB -> itemService.getJobStories()
		}
		return streamItems(items)
	}

	override fun getChildren(item: Item): Observable<List<Item>> {
		return getChildrenObservable(item)
				.buffer(BUFFER)
				.scan { prev: List<Item>, next: List<Item> -> prev + next }
	}

	override fun getAll(): Observable<List<Item>> {
		throw UnsupportedOperationException("Cannot get all items from network.")
	}

	override fun getById(id: Long): Observable<Item> {
		return itemService.getById(id)
	}

	@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
	override fun save(item: Item): Observable<Boolean> {
		throw UnsupportedOperationException("Cannot save items to network.")
	}

	// Private functions

	private fun getChildrenObservable(item: Item, level: Int = 0): Observable<Item> {
		val kids = item.kids
		if (kids == null || kids.isEmpty()) {
			return Observable.just(item)
		}

		val childObservable = Observable.from(item.kids.orEmpty())
				.concatMapEager { getById(it) }
				.map { it.copy(level = level) }
				.filter { it.deleted != true }
				.concatMapEager { getChildrenObservable(it, level + 1) }

		return Observable.just(item)
				.concatWith(childObservable)
	}

	private fun streamItems(itemIdResponse: Observable<List<Long>>): Observable<List<Item>> {
		return itemIdResponse
				.flatMapIterable { it }
				.concatMapEager { itemService.getById(it) }
				.buffer(BUFFER)
				.scan { prev: List<Item>, next: List<Item> -> prev + next }
	}
}