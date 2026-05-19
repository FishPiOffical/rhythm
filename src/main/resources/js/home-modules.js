/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
$.extend(HomePersonalize, {
  initHomeModules: function () {
    HomePersonalize.modules = HomePersonalize.collectModules()
    if (HomePersonalize.modules.length === 0) {
      return
    }
    HomePersonalize.renderPanel()
    HomePersonalize.bindPanel()
    HomePersonalize.bindDrag()
    HomePersonalize.applyLayout()
  },

  collectModules: function () {
    var ret = []
    HomePersonalize.zones = {}
    HomePersonalize.zoneIndex = 0
    $('.home-module[data-home-module]').each(function () {
      ret.push(HomePersonalize.readModule($(this)))
    })
    return ret
  },

  readModule: function ($module) {
    var $zone = $module.parent()
    var zoneKey = HomePersonalize.getZoneKey($zone)
    return {
      id: $module.data('home-module'),
      title: $module.data('home-title') || $module.data('home-module'),
      $module: $module,
      $zone: $zone,
      zoneKey: zoneKey,
      defaultZoneKey: zoneKey,
      defaultIndex: $module.index(),
      defaultNextNode: $module.next()[0] || null,
      defaultPrevNode: $module.prev()[0] || null,
    }
  },

  getZoneKey: function ($zone) {
    var key = $zone.data('home-zone')
    if (!key) {
      key = 'auto-' + HomePersonalize.zoneIndex
      HomePersonalize.zoneIndex++
      $zone.attr('data-home-zone', key).data('home-zone', key)
    }
    HomePersonalize.zones[key] = $zone
    return key
  },

  renderPanel: function () {
    $('#homePersonalizePanel').html(HomePersonalize.buildPanelHTML())
  },

  buildPanelHTML: function () {
    return '<div class="home-personalize-panel__head"><b>首页模块</b>'
      + '<button type="button" data-home-panel-action="close">关闭</button></div>'
      + '<div class="home-personalize-panel__list">' + HomePersonalize.buildPanelItems()
      + '</div>' + HomePersonalize.buildPanelActions()
  },

  buildPanelActions: function () {
    var actions = []
    var moduleCount = HomePersonalize.modules.length
    if (moduleCount > HomePersonalize.visiblePresetSizes.compact) {
      actions.push('<button type="button" data-home-panel-action="compact">精简6</button>')
    }
    if (moduleCount >= HomePersonalize.visiblePresetSizes.standard) {
      actions.push('<button type="button" data-home-panel-action="standard">标准8</button>')
    }
    actions.push('<button type="button" data-home-panel-action="reset">重置</button>')
    return '<div class="home-personalize-panel__actions">' + actions.join('') + '</div>'
  },

  buildPanelItems: function () {
    var state = HomePersonalize.getState()
    var modules = HomePersonalize.getPanelModules()
    return modules.map(function (module, index) {
      var checked = state.hidden[module.id] ? '' : ' checked'
      var id = HomePersonalize.escape(module.id)
      var title = HomePersonalize.escape(module.title)
      var upDisabled = index === 0 ? ' disabled' : ''
      var downDisabled = index === modules.length - 1 ? ' disabled' : ''
      return '<div class="home-personalize-panel__item" data-home-panel-id="' + id + '" draggable="true">'
        + '<label class="home-personalize-panel__check">'
        + '<input type="checkbox" data-home-module-toggle="' + id + '"' + checked + '>'
        + '<span>' + title + '</span></label>'
        + '<span class="home-personalize-panel__sort">'
        + '<button type="button" data-home-panel-move="up" data-home-panel-target="' + id + '" aria-label="上移' + title + '"' + upDisabled + '>上移</button>'
        + '<button type="button" data-home-panel-move="down" data-home-panel-target="' + id + '" aria-label="下移' + title + '"' + downDisabled + '>下移</button>'
        + '</span></div>'
    }).join('')
  },

  getPanelModules: function () {
    return HomePersonalize.getOrderedModules(HomePersonalize.getState())
  },

  getOrderedModules: function (state) {
    var used = {}
    var order = state.order && state.order.length ? state.order : HomePersonalize.getDefaultOrder()
    var ordered = order.map(function (id) {
      var module = HomePersonalize.findModule(id)
      if (module) {
        used[module.id] = true
      }
      return module
    }).filter(function (module) {
      return !!module
    })
    HomePersonalize.modules.forEach(function (module) {
      if (!used[module.id]) {
        ordered.push(module)
      }
    })
    return ordered
  },

  getDefaultOrder: function () {
    return HomePersonalize.modules.map(function (module) {
      return module.id
    })
  },

  bindPanel: function () {
    $('#homePersonalizeOpen').off('click.homePersonalize').on('click.homePersonalize', function () {
      $('#homePersonalizePanel').toggleClass('home-personalize-panel--open')
    })
    $('#homePersonalizePanel').off('click.homePersonalize')
      .on('click.homePersonalize', '[data-home-panel-action]', HomePersonalize.handlePanelAction)
      .on('click.homePersonalize', '[data-home-panel-move]', HomePersonalize.handlePanelMove)
      .on('change.homePersonalize', '[data-home-module-toggle]', HomePersonalize.toggleModule)
      .on('dragstart.homePersonalize', '[data-home-panel-id]', HomePersonalize.startPanelDrag)
      .on('dragover.homePersonalize', '[data-home-panel-id]', HomePersonalize.allowPanelDrop)
      .on('drop.homePersonalize', '[data-home-panel-id]', HomePersonalize.dropPanelItem)
      .on('dragend.homePersonalize', HomePersonalize.endPanelDrag)
  },

  startPanelDrag: function (event) {
    HomePersonalize.panelDraggedId = $(this).data('home-panel-id')
    $(this).addClass('home-personalize-panel__item--dragging')
    event.originalEvent.dataTransfer.effectAllowed = 'move'
  },

  allowPanelDrop: function (event) {
    event.preventDefault()
    event.originalEvent.dataTransfer.dropEffect = 'move'
  },

  dropPanelItem: function (event) {
    event.preventDefault()
    var targetId = $(this).data('home-panel-id')
    HomePersonalize.moveModule(HomePersonalize.panelDraggedId, targetId)
    HomePersonalize.renderPanel()
  },

  endPanelDrag: function () {
    HomePersonalize.panelDraggedId = ''
    $('.home-personalize-panel__item--dragging').removeClass('home-personalize-panel__item--dragging')
  },

  handlePanelAction: function () {
    var action = $(this).data('home-panel-action')
    if (action === 'close') {
      $('#homePersonalizePanel').removeClass('home-personalize-panel--open')
      return
    }
    if (action === 'reset') {
      HomePersonalize.resetLayout()
    }
    if (action === 'compact' || action === 'standard') {
      HomePersonalize.applyVisiblePreset(HomePersonalize.visiblePresetSizes[action])
    }
  },

  handlePanelMove: function (event) {
    event.preventDefault()
    event.stopPropagation()
    var id = $(this).data('home-panel-target')
    var direction = $(this).data('home-panel-move')
    HomePersonalize.movePanelItem(id, direction)
  },

  movePanelItem: function (id, direction) {
    var modules = HomePersonalize.getPanelModules()
    var index = HomePersonalize.findPanelIndex(modules, id)
    if (index < 0) {
      return
    }
    var targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= modules.length) {
      return
    }
    var position = direction === 'up' ? 'before' : 'after'
    if (HomePersonalize.placeModule(id, modules[targetIndex].id, position)) {
      HomePersonalize.renderPanel()
    }
  },

  findPanelIndex: function (modules, id) {
    for (var i = 0; i < modules.length; i++) {
      if (modules[i].id === id) {
        return i
      }
    }
    return -1
  },

  resetLayout: function () {
    localStorage.removeItem(HomePersonalize.storageKey)
    HomePersonalize.restoreDefaultLayout()
    HomePersonalize.renderPanel()
    HomePersonalize.applyLayout()
  },

  restoreDefaultLayout: function () {
    HomePersonalize.modules.slice().reverse().forEach(function (module) {
      HomePersonalize.restoreModulePosition(module)
    })
  },

  restoreModulePosition: function (module) {
    if (module.defaultNextNode && $.contains(module.$zone[0], module.defaultNextNode)) {
      $(module.defaultNextNode).before(module.$module)
      return
    }
    if (module.defaultPrevNode && $.contains(module.$zone[0], module.defaultPrevNode)) {
      $(module.defaultPrevNode).after(module.$module)
      return
    }
    module.$zone.append(module.$module)
  },

  toggleModule: function () {
    var id = $(this).data('home-module-toggle')
    var state = HomePersonalize.getState()
    state.hidden[id] = !this.checked
    HomePersonalize.setState(state)
    HomePersonalize.applyLayout()
  },

  applyVisiblePreset: function (count) {
    var state = HomePersonalize.getState()
    var visible = {}
    HomePersonalize.getOrderedModules(state).forEach(function (module, index) {
      visible[module.id] = index < count
    })
    HomePersonalize.modules.forEach(function (module) {
      state.hidden[module.id] = !visible[module.id]
    })
    HomePersonalize.setState(state)
    HomePersonalize.renderPanel()
    HomePersonalize.applyLayout()
  },

  getCurrentOrder: function () {
    var order = $('.home-module[data-home-module]').map(function () {
      return $(this).data('home-module')
    }).get()
    return order.length ? order : HomePersonalize.modules.map(function (module) {
      return module.id
    })
  },

  bindDrag: function () {
    var draggedId = ''
    $('.home-module[data-home-module]').attr('draggable', 'true')
      .off('dragstart.homePersonalize dragover.homePersonalize drop.homePersonalize dragend.homePersonalize')
      .on('dragstart.homePersonalize', function (event) {
        draggedId = HomePersonalize.startDrag($(this), event)
      })
      .on('dragover.homePersonalize', HomePersonalize.allowDrop)
      .on('drop.homePersonalize', function (event) {
        HomePersonalize.dropModule(draggedId, $(this), event)
      })
      .on('dragend.homePersonalize', function () {
        $('.home-module--dragging').removeClass('home-module--dragging')
      })
  },

  startDrag: function ($module, event) {
    $module.addClass('home-module--dragging')
    event.originalEvent.dataTransfer.effectAllowed = 'move'
    return $module.data('home-module')
  },

  allowDrop: function (event) {
    event.preventDefault()
    event.originalEvent.dataTransfer.dropEffect = 'move'
  },

  dropModule: function (sourceId, $target, event) {
    event.preventDefault()
    HomePersonalize.moveModule(sourceId, $target.data('home-module'))
  },

  moveModule: function (sourceId, targetId) {
    HomePersonalize.placeModule(sourceId, targetId, 'before')
  },

  placeModule: function (sourceId, targetId, position) {
    if (!sourceId || !targetId || sourceId === targetId) {
      return false
    }
    var pair = HomePersonalize.getMovePair(sourceId, targetId)
    if (!pair) {
      return false
    }
    if (position === 'after') {
      pair.$target.after(pair.$source)
    } else {
      pair.$target.before(pair.$source)
    }
    HomePersonalize.storeLayout()
    return true
  },

  getMovePair: function (sourceId, targetId) {
    var $source = $('[data-home-module="' + sourceId + '"]').first()
    var $target = $('[data-home-module="' + targetId + '"]').first()
    if (!$source.length || !$target.length) {
      return null
    }
    return {$source: $source, $target: $target}
  },

  storeLayout: function () {
    var state = HomePersonalize.getState()
    state.order = $('.home-module[data-home-module]').map(function () {
      return $(this).data('home-module')
    }).get()
    state.zones = HomePersonalize.collectCurrentZones()
    HomePersonalize.setState(state)
  },

  collectCurrentZones: function () {
    var zones = {}
    $('.home-module[data-home-module]').each(function () {
      var $module = $(this)
      zones[$module.data('home-module')] = HomePersonalize.getZoneKey($module.parent())
    })
    return zones
  },

  applyLayout: function () {
    var state = HomePersonalize.getState()
    HomePersonalize.applyVisibility(state)
    HomePersonalize.applyOrder(state.order || [], state.zones || {})
    HomePersonalize.updateZoneVisibility()
  },

  applyVisibility: function (state) {
    HomePersonalize.modules.forEach(function (module) {
      module.$module.toggle(!state.hidden[module.id])
    })
  },

  applyOrder: function (order, zones) {
    var modulesByZone = {}
    var orderedModules = HomePersonalize.mergeOrderedModules(order)
    orderedModules.forEach(function (module) {
      var zoneKey = zones[module.id]
      if (!zoneKey || !HomePersonalize.zones[zoneKey]) {
        zoneKey = module.defaultZoneKey
      }
      if (!modulesByZone[zoneKey]) {
        modulesByZone[zoneKey] = []
      }
      modulesByZone[zoneKey].push(module)
    })
    Object.keys(modulesByZone).forEach(function (zoneKey) {
      HomePersonalize.placeModulesInZone(modulesByZone[zoneKey], zoneKey)
    })
  },

  mergeOrderedModules: function (order) {
    var used = {}
    var orderedModules = order.map(function (id) {
      var module = HomePersonalize.findModule(id)
      if (module) {
        used[module.id] = true
      }
      return module
    }).filter(function (module) {
      return !!module
    })
    HomePersonalize.modules.forEach(function (module) {
      if (!used[module.id]) {
        orderedModules.push(module)
      }
    })
    return orderedModules
  },

  placeModulesInZone: function (modules, zoneKey) {
    var $zone = HomePersonalize.zones[zoneKey]
    if (!$zone) {
      return
    }
    var $anchor = HomePersonalize.getZoneEndAnchor(zoneKey)
    modules.forEach(function (module) {
      if ($anchor.length) {
        $anchor.before(module.$module)
        return
      }
      $zone.append(module.$module)
    })
  },

  updateZoneVisibility: function () {
    $('.home-personalize-zone').each(function () {
      var hasVisibleModule = $(this).children('.home-module[data-home-module]').filter(function () {
        return $(this).css('display') !== 'none'
      }).length > 0
      $(this).toggle(hasVisibleModule)
    })
  },

  getZoneEndAnchor: function (zoneKey) {
    for (var i = HomePersonalize.modules.length - 1; i >= 0; i--) {
      var module = HomePersonalize.modules[i]
      if (module.defaultZoneKey === zoneKey
        && module.defaultNextNode
        && $.contains(module.$zone[0], module.defaultNextNode)) {
        return $(module.defaultNextNode)
      }
    }
    return $()
  },

  findModule: function (id) {
    for (var i = 0; i < HomePersonalize.modules.length; i++) {
      if (HomePersonalize.modules[i].id === id) {
        return HomePersonalize.modules[i]
      }
    }
    return null
  },
})
