/**
 * 切换tab选项卡
 * @param contentClass 内容的class
 * @param tabClass tab的class
 * @param showIndex 要显示的下标
 */
function changeType(contentClass, tabClass, showIndex) {
    let contentDomList = document.querySelectorAll(`.${contentClass}`);
    let tabDomList = document.querySelectorAll(`.${tabClass}`);
    contentDomList.forEach((item) => {
        item.classList.remove('active');
    })
    tabDomList.forEach((item) => {
        item.classList.remove('active');
    })
    contentDomList[showIndex].classList.add('active');
    tabDomList[showIndex].classList.add('active');
}