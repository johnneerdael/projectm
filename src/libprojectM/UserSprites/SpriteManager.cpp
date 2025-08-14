#include "UserSprites/SpriteManager.hpp"

#include "UserSprites/Factory.hpp"

#include <Renderer/Shader.hpp>

#include <algorithm>

namespace libprojectM {
namespace UserSprites {

auto SpriteManager::Spawn(const std::string& type,
                          const std::string& spriteData,
                          const Renderer::RenderContext& renderContext) -> uint32_t
{
    // Android TV: More restrictive sprite limits to prevent memory issues
    if (m_spriteSlots == 0 || m_spriteSlots > 16)
    {
        return 0;
    }

    // Android TV: Validate sprite data size to prevent excessive memory usage
    if (spriteData.empty() || spriteData.length() > 32768)  // 32KB limit
    {
        return 0;
    }

    auto sprite = Factory::CreateSprite(type);

    if (!sprite)
    {
        return 0;
    }

    try
    {
        sprite->Init(spriteData, renderContext);
    }
    catch (SpriteException& ex)
    {
        return 0;
    }
    catch (Renderer::ShaderException& ex)
    {
        return 0;
    }
    catch (const std::bad_alloc&)
    {
        // Android TV: Handle memory allocation failures
        return 0;
    }
    catch (...)
    {
        return 0;
    }

    auto spriteIdentifier = GetLowestFreeIdentifier();

    // Already at max sprites, destroy the oldest sprite to make room.
    if (m_sprites.size() >= m_spriteSlots)
    {
        Destroy(m_sprites.front().first);
    }

    m_sprites.emplace_back(spriteIdentifier, std::move(sprite));
    m_spriteIdentifiers.insert(spriteIdentifier);

    return spriteIdentifier;
}

void SpriteManager::Draw(const Audio::FrameAudioData& audioData,
                         const Renderer::RenderContext& renderContext,
                         uint32_t outputFramebufferObject,
                         Sprite::PresetList presets)
{
    // Android TV: Early exit if no sprites or invalid context
    if (m_sprites.empty() || renderContext.viewportSizeX <= 0 || renderContext.viewportSizeY <= 0)
    {
        return;
    }

    // Android TV: Validate framebuffer before drawing sprites
    GLint currentFbo;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &currentFbo);
    if (currentFbo != static_cast<GLint>(outputFramebufferObject))
    {
        glBindFramebuffer(GL_FRAMEBUFFER, outputFramebufferObject);
    }

    // Check framebuffer completeness for Android TV compatibility
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    {
        return;
    }

    // Android TV: Process sprites with iterator to allow safe deletion during iteration
    auto it = m_sprites.begin();
    while (it != m_sprites.end())
    {
        auto& idAndSprite = *it;
        
        try
        {
            idAndSprite.second->Draw(audioData, renderContext, outputFramebufferObject, presets);
            
            if (idAndSprite.second->Done())
            {
                auto spriteId = idAndSprite.first;
                ++it;  // Advance iterator before deletion
                Destroy(spriteId);
            }
            else
            {
                ++it;
            }
        }
        catch (...)
        {
            // Android TV: Handle drawing exceptions gracefully
            auto spriteId = idAndSprite.first;
            ++it;
            Destroy(spriteId);
        }
    }
}

void SpriteManager::Destroy(SpriteIdentifier spriteIdentifier)
{
    if (m_spriteIdentifiers.find(spriteIdentifier) == m_spriteIdentifiers.end())
    {
        return;
    }

    m_spriteIdentifiers.erase(spriteIdentifier);
    m_sprites.remove_if([spriteIdentifier](const auto& idAndSprite) {
        return idAndSprite.first == spriteIdentifier;
    });
}

void SpriteManager::DestroyAll()
{
    m_spriteIdentifiers.clear();
    m_sprites.clear();
}

auto SpriteManager::ActiveSpriteCount() const -> uint32_t
{
    return m_sprites.size();
}

auto SpriteManager::ActiveSpriteIdentifiers() const -> std::vector<SpriteIdentifier>
{
    std::vector<SpriteIdentifier> identifierList;
    for (auto& idAndSprite : m_sprites) {
        identifierList.emplace_back(idAndSprite.first);
    }

    return identifierList;
}

void SpriteManager::SpriteSlots(uint32_t slots)
{
    // Android TV: Enforce maximum sprite limit for memory management
    constexpr uint32_t MAX_ANDROID_TV_SPRITES = 8;
    m_spriteSlots = std::min(slots, MAX_ANDROID_TV_SPRITES);

    // Remove excess sprites if limit was lowered
    while (m_sprites.size() > m_spriteSlots)
    {
        m_spriteIdentifiers.erase(m_sprites.front().first);
        m_sprites.pop_front();
    }
}

auto SpriteManager::SpriteSlots() const -> uint32_t
{
    return m_spriteSlots;
}

auto SpriteManager::GetLowestFreeIdentifier() -> SpriteIdentifier
{
    SpriteIdentifier lowestId = 0;

    for (const auto& spriteId : m_spriteIdentifiers)
    {
        if (spriteId > lowestId + 1)
        {
            return lowestId + 1;
        }

        lowestId = spriteId;
    }

    return lowestId + 1;
}

} // namespace UserSprites
} // namespace libprojectM
